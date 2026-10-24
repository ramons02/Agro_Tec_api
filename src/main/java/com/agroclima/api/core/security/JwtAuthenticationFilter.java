package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.StatusConta;
import com.agroclima.api.core.response.AppException;
import com.agroclima.api.core.response.ErrorEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Extrai e valida o Bearer token; ausencia/invalidez nao bloqueia aqui, vira 401 mais adiante via
 * authenticated(). Alem da assinatura, confere o estado da conta a cada requisicao (spec 022): conta
 * inexistente ou com versao de sessao diferente da do token cai em 401, conta SUSPENSA recebe 403
 * "conta suspensa" e o token restrito do primeiro acesso so alcanca POST /auth/trocar-senha.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String ROTA_TROCAR_SENHA = "/api/v1/auth/trocar-senha";

    /** Rotas publicas: um token velho no navegador nao pode barrar nem alterar o login, o cadastro ou a recuperacao. */
    private static final Set<String> ROTAS_PUBLICAS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/registro",
            "/api/v1/auth/recuperar-senha",
            "/api/v1/auth/redefinir-senha");

    private final JwtService jwtService;
    private final EstadoContaCache estadoContaCache;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService, EstadoContaCache estadoContaCache, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.estadoContaCache = estadoContaCache;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && !ROTAS_PUBLICAS.contains(request.getRequestURI())) {
            try {
                JwtService.TokenDecodificado token = jwtService.decodificarCompleto(header.substring(7));
                Optional<EstadoConta> estado = estadoContaCache.obter(token.usuario().id());
                if (estado.isEmpty()
                        || estado.get().versaoSessao() != token.versaoSessao()
                        || estado.get().status() == StatusConta.PENDENTE) {
                    SecurityContextHolder.clearContext();
                } else if (estado.get().status() == StatusConta.SUSPENSA) {
                    recusar(response, "Conta suspensa. Procure o administrador.");
                    return;
                } else if (token.restrito() && !ehTrocaDeSenha(request)) {
                    recusar(response, "Troca de senha obrigatória.");
                    return;
                } else {
                    // O papel vem do banco, nao do token: se o papel da conta mudou depois da emissao do
                    // token (spec 024, R9), vale o atual, e nao o que o token ainda diz.
                    UsuarioAutenticado usuario = new UsuarioAutenticado(token.usuario().id(), estado.get().papel());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.papel().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (AppException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean ehTrocaDeSenha(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && ROTA_TROCAR_SENHA.equals(request.getRequestURI());
    }

    private void recusar(HttpServletResponse response, String mensagem) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorEnvelope.of(403, mensagem, null)));
    }
}
