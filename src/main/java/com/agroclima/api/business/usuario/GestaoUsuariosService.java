package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.propriedade.PropriedadeRepository;
import com.agroclima.api.business.vinculo.EstadoVinculo;
import com.agroclima.api.business.vinculo.VinculoAgronomoPropriedadeRepository;
import com.agroclima.api.core.base.ContagemPorUsuario;
import com.agroclima.api.core.security.AutorizacaoService;
import com.agroclima.api.core.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monitoramento de contas para o gestor de tecnologia: quem se cadastrou (produtores, agronomos e outros
 * gestores), quando, e quanto cada um ja usa o sistema. So leitura; nunca devolve hash de senha.
 */
@Service
public class GestaoUsuariosService {

    static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final UsuarioRepository usuarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final VinculoAgronomoPropriedadeRepository vinculoRepository;
    private final AutorizacaoService autorizacaoService;

    public GestaoUsuariosService(
            UsuarioRepository usuarioRepository,
            PropriedadeRepository propriedadeRepository,
            VinculoAgronomoPropriedadeRepository vinculoRepository,
            AutorizacaoService autorizacaoService) {
        this.usuarioRepository = usuarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.vinculoRepository = vinculoRepository;
        this.autorizacaoService = autorizacaoService;
    }

    /** propriedades: produtor = as que ele possui; agronomo = as com vinculo aceito; gestor = 0. */
    public record ItemUsuario(
            UUID id,
            String nome,
            String email,
            Papel papel,
            Instant criadoEm,
            boolean telegramVinculado,
            long propriedades) {}

    public record Resumo(
            long total, Map<Papel, Long> porPapel, long cadastrosSemana, long cadastrosMes, Instant ultimoCadastroEm) {}

    @Transactional(readOnly = true)
    public Page<ItemUsuario> listar(UsuarioAutenticado solicitante, Papel papel, String busca, int page, int pageSize) {
        autorizacaoService.exigirGestor(solicitante);

        int tamanho = Math.min(Math.max(pageSize, 1), TAMANHO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0), tamanho, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id")));
        Page<Usuario> pagina = usuarioRepository.findAll(filtro(papel, busca), pageable);

        Map<UUID, Long> propriedadesPorUsuario = contarPropriedades(pagina.getContent());
        List<ItemUsuario> itens = pagina.getContent().stream()
                .map(u -> new ItemUsuario(
                        u.getId(),
                        u.getNome(),
                        u.getEmail(),
                        u.getPapel(),
                        u.getCriadoEm(),
                        u.getTelegramChatId() != null && !u.getTelegramChatId().isBlank(),
                        propriedadesPorUsuario.getOrDefault(u.getId(), 0L)))
                .toList();
        return new PageImpl<>(itens, pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Resumo resumir(UsuarioAutenticado solicitante) {
        autorizacaoService.exigirGestor(solicitante);

        Instant agora = Instant.now();
        Map<Papel, Long> porPapel = new EnumMap<>(Papel.class);
        for (Papel papel : Papel.values()) {
            porPapel.put(papel, usuarioRepository.countByPapel(papel));
        }
        return new Resumo(
                usuarioRepository.count(),
                porPapel,
                usuarioRepository.countByCriadoEmAfter(agora.minus(Duration.ofDays(7))),
                usuarioRepository.countByCriadoEmAfter(agora.minus(Duration.ofDays(30))),
                usuarioRepository.findFirstByOrderByCriadoEmDesc().map(Usuario::getCriadoEm).orElse(null));
    }

    private Specification<Usuario> filtro(Papel papel, String busca) {
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (papel != null) {
                condicoes.add(cb.equal(root.get("papel"), papel));
            }
            if (busca != null && !busca.isBlank()) {
                String padrao = "%" + escaparCuringas(busca.trim().toLowerCase(Locale.ROOT)) + "%";
                condicoes.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), padrao, '\\'),
                        cb.like(cb.lower(root.get("email")), padrao, '\\')));
            }
            return cb.and(condicoes.toArray(new Predicate[0]));
        };
    }

    /** O termo digitado e literal: %, _ e \ nao viram curinga do LIKE. */
    private static String escaparCuringas(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<UUID, Long> contarPropriedades(List<Usuario> usuarios) {
        List<UUID> produtores = idsDoPapel(usuarios, Papel.PRODUTOR_RURAL);
        List<UUID> agronomos = idsDoPapel(usuarios, Papel.AGRONOMO);
        Map<UUID, Long> contagem = new java.util.HashMap<>();
        if (!produtores.isEmpty()) {
            contagem.putAll(paraMapa(propriedadeRepository.contarPorProprietario(produtores)));
        }
        if (!agronomos.isEmpty()) {
            contagem.putAll(paraMapa(vinculoRepository.contarPorAgronomo(agronomos, EstadoVinculo.ACEITO)));
        }
        return contagem;
    }

    private static List<UUID> idsDoPapel(List<Usuario> usuarios, Papel papel) {
        return usuarios.stream().filter(u -> u.getPapel() == papel).map(Usuario::getId).toList();
    }

    private static Map<UUID, Long> paraMapa(List<ContagemPorUsuario> contagens) {
        return contagens.stream().collect(Collectors.toMap(ContagemPorUsuario::getUsuarioId, ContagemPorUsuario::getTotal));
    }
}
