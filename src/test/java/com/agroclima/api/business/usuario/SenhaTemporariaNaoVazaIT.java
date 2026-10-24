package com.agroclima.api.business.usuario;

import com.agroclima.api.business.auth.Papel;
import com.agroclima.api.business.auth.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-014 / SC-004: a senha temporaria aparece so na resposta da acao; nunca em log nem em consulta posterior. */
@ExtendWith(OutputCaptureExtension.class)
class SenhaTemporariaNaoVazaIT extends GestaoContasITBase {

    @Test
    void confirmarCriarERedefinirNaoEscrevemASenhaNoLog(CapturedOutput saida) {
        Usuario pendente = pendenteNoGrupo("pend@teste.com");
        Usuario ativa = conta("ativa@teste.com", Papel.AGRONOMO);

        String confirmada = senhaDe(chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor, null));
        String criada = senhaDe(chamar(HttpMethod.POST, "/api/v1/usuarios", tokenGestor,
                Map.of("nome", "Nova", "email", "nova@teste.com", "papel", "PRODUTOR_RURAL")));
        String redefinida = senhaDe(chamar(HttpMethod.POST, "/api/v1/usuarios/" + ativa.getId() + "/redefinir-senha", tokenGestor, null));

        assertThat(saida.getAll()).doesNotContain(confirmada).doesNotContain(criada).doesNotContain(redefinida);
    }

    @Test
    void nenhumaConsultaPosteriorDevolveASenha() {
        Usuario pendente = pendenteNoGrupo("pend2@teste.com");
        String senha = senhaDe(chamar(HttpMethod.POST, "/api/v1/usuarios/" + pendente.getId() + "/confirmar", tokenGestor, null));

        String lista = chamar(HttpMethod.GET, "/api/v1/usuarios", tokenGestor, null).getBody().toString();
        String detalhe = chamar(HttpMethod.GET, "/api/v1/usuarios/" + pendente.getId(), tokenGestor, null).getBody().toString();
        String resumo = chamar(HttpMethod.GET, "/api/v1/usuarios/resumo", tokenGestor, null).getBody().toString();

        assertThat(lista).doesNotContain(senha);
        assertThat(detalhe).doesNotContain(senha);
        assertThat(resumo).doesNotContain(senha);
    }

    // Como se tivesse chegado pelo link do grupo: o Master so confirma pendente sem grupo escolhendo um.
    private Usuario pendenteNoGrupo(String email) {
        Usuario pendente = Usuario.pendente("Pendente", email, Papel.PRODUTOR_RURAL, Instant.now());
        pendente.definirGrupo(grupoInicial.getId());
        return usuarioRepository.save(pendente);
    }

    private String senhaDe(ResponseEntity<Map> resposta) {
        return (String) dados(resposta).get("senha_temporaria");
    }
}
