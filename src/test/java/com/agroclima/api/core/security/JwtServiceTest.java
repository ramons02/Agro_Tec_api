package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.core.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static JwtService servico(String segredo) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(segredo, "HS256", 24), null, null, null, null, null);
        return new JwtService(props);
    }

    @Test
    void segredoAusenteImpedeInicializacao() {
        assertThrows(IllegalStateException.class, () -> servico(null));
    }

    @Test
    void segredoCurtoImpedeInicializacao() {
        assertThrows(IllegalStateException.class, () -> servico("curto"));
    }

    @Test
    void segredoValidoAssinaEValidaToken() {
        JwtService jwt = servico("segredo-valido-com-mais-de-trinta-e-dois-bytes");
        UUID id = UUID.randomUUID();

        UsuarioAutenticado usuario = jwt.decodificar(jwt.criarToken(id, Papel.PRODUTOR_RURAL).token());

        assertEquals(id, usuario.id());
        assertEquals(Papel.PRODUTOR_RURAL, usuario.papel());
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRecusado() {
        JwtService emissor = servico("segredo-valido-com-mais-de-trinta-e-dois-bytes");
        JwtService outro = servico("outro-segredo-valido-com-mais-de-trinta-bytes");
        String token = emissor.criarToken(UUID.randomUUID(), Papel.GESTOR_TECNOLOGIA).token();

        assertThrows(com.agroclima.api.core.response.AppException.class, () -> outro.decodificar(token));
    }
}
