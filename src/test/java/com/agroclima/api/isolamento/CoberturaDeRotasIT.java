package com.agroclima.api.isolamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rede de seguranca da suite de isolamento (spec 024, R10): percorre todas as rotas que a aplicacao expoe e falha
 * se alguma nao estiver numa das tres listas de {@link IsolamentoBase} (dados, gestao ou "sem escopo de grupo por
 * desenho"). Uma rota nova que le ou altera dado de alguem tem de ser classificada e coberta, ou o build quebra.
 */
class CoberturaDeRotasIT extends IsolamentoBase {

    @Autowired
    private RequestMappingHandlerMapping mapeamento;

    private Set<String> rotasDaAplicacao() {
        Set<String> rotas = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeamento.getHandlerMethods().entrySet()) {
            HandlerMethod metodo = entrada.getValue();
            // So as rotas dos nossos controladores (nao as do actuator, do Spring Data ou de erro).
            if (!metodo.getBeanType().isAnnotationPresent(RestController.class)
                    || !metodo.getBeanType().getName().startsWith("com.agroclima.api")) {
                continue;
            }
            Set<String> caminhos = entrada.getKey().getPathPatternsCondition() != null
                    ? entrada.getKey().getPathPatternsCondition().getPatternValues()
                    : Set.of();
            for (var metodoHttp : entrada.getKey().getMethodsCondition().getMethods()) {
                for (String caminho : caminhos) {
                    rotas.add(metodoHttp.name() + " " + caminho);
                }
            }
        }
        return rotas;
    }

    @Test
    void todaRotaDaAplicacaoEstaClassificadaNaSuiteDeIsolamento() {
        Set<String> classificadas = new TreeSet<>();
        classificadas.addAll(ROTAS_DE_DADOS);
        classificadas.addAll(ROTAS_DE_GESTAO);
        classificadas.addAll(ROTAS_SEM_ESCOPO_DE_GRUPO);

        Set<String> semClassificacao = new TreeSet<>(rotasDaAplicacao());
        semClassificacao.removeAll(classificadas);

        assertThat(semClassificacao)
                .as("Rotas novas sem classificacao: coloque-as em ROTAS_DE_DADOS (e teste o isolamento), "
                        + "ROTAS_DE_GESTAO ou, se de fato nao tem escopo de grupo, em ROTAS_SEM_ESCOPO_DE_GRUPO")
                .isEmpty();
    }

    @Test
    void asListasNaoGuardamRotasQueNaoExistemMais() {
        Set<String> existentes = rotasDaAplicacao();
        Set<String> listadas = new TreeSet<>();
        listadas.addAll(ROTAS_DE_DADOS);
        listadas.addAll(ROTAS_DE_GESTAO);
        listadas.addAll(ROTAS_SEM_ESCOPO_DE_GRUPO);

        listadas.removeAll(existentes);

        assertThat(listadas).as("Rotas listadas que nao existem mais").isEmpty();
    }

    @Test
    void asTresListasNaoSeSobrepoem() {
        assertThat(ROTAS_DE_DADOS).doesNotContainAnyElementsOf(ROTAS_DE_GESTAO);
        assertThat(ROTAS_DE_DADOS).doesNotContainAnyElementsOf(ROTAS_SEM_ESCOPO_DE_GRUPO);
        assertThat(ROTAS_DE_GESTAO).doesNotContainAnyElementsOf(ROTAS_SEM_ESCOPO_DE_GRUPO);
    }
}
