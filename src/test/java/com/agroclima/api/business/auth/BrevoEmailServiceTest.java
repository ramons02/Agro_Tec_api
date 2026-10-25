package com.agroclima.api.business.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatCode;

class BrevoEmailServiceTest {

    private static final WireMockServer MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void iniciar() {
        MOCK.start();
    }

    @AfterAll
    static void parar() {
        MOCK.stop();
    }

    @BeforeEach
    void preparar() {
        MOCK.resetAll();
    }

    private BrevoEmailService servico() {
        return new BrevoEmailService(MOCK.baseUrl(), "chave-secreta", "no-reply@exemplo.com", "AgroClima Pará");
    }

    @Test
    void enviaOLinkPelaApiComAChaveNoCabecalho() {
        MOCK.stubFor(post(urlEqualTo("/v3/smtp/email")).willReturn(aResponse().withStatus(201).withBody("{\"messageId\":\"x\"}")));

        servico().enviarRecuperacaoSenha("produtor@exemplo.com", "https://app/#/redefinir-senha?token=abc");

        MOCK.verify(postRequestedFor(urlEqualTo("/v3/smtp/email"))
                .withHeader("api-key", equalTo("chave-secreta"))
                .withRequestBody(equalToJson("{\"sender\":{\"email\":\"no-reply@exemplo.com\",\"name\":\"AgroClima Pará\"},"
                        + "\"to\":[{\"email\":\"produtor@exemplo.com\"}],"
                        + "\"subject\":\"Redefinição de senha, AgroClima Pará\"}", true, true))
                .withRequestBody(containing("token=abc")));
    }

    @Test
    void falhaDoProvedorNaoPropagaExcecao() {
        MOCK.stubFor(post(urlEqualTo("/v3/smtp/email")).willReturn(aResponse().withStatus(401)));

        assertThatCode(() -> servico().enviarRecuperacaoSenha("a@b.com", "https://app/link")).doesNotThrowAnyException();
    }

    @Test
    void provedorForaDoArNaoPropagaExcecao() {
        BrevoEmailService servico = servico();
        MOCK.stop();
        try {
            assertThatCode(() -> servico.enviarRecuperacaoSenha("a@b.com", "https://app/link")).doesNotThrowAnyException();
        } finally {
            MOCK.start();
        }
    }
}
