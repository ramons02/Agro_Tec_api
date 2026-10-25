package com.agroclima.api.business.push;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorDeEndpointTest {

    private final ValidadorDeEndpoint producao = new ValidadorDeEndpoint(false);

    @Test
    void aceitaOsServicosDePushDosNavegadores() {
        assertThat(producao.valido("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(producao.valido("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(producao.valido("https://web.push.apple.com/abc")).isTrue();
        assertThat(producao.valido("https://wns2-par02p.notify.windows.com/w/?token=abc")).isTrue();
    }

    @Test
    void recusaEnderecoInternoOuDeFora() {
        assertThat(producao.valido("http://fcm.googleapis.com/fcm/send/abc")).isFalse(); // sem https
        assertThat(producao.valido("https://localhost/x")).isFalse();
        assertThat(producao.valido("http://127.0.0.1:8080/x")).isFalse();
        assertThat(producao.valido("https://169.254.169.254/latest/meta-data")).isFalse();
        assertThat(producao.valido("https://10.0.0.5/x")).isFalse();
        assertThat(producao.valido("https://fcm.googleapis.com.evil.com/x")).isFalse(); // dominio parecido
        assertThat(producao.valido("https://evilfcm.googleapis.com.br/x")).isFalse();
        assertThat(producao.valido("https://user:senha@fcm.googleapis.com/x")).isFalse();
        assertThat(producao.valido("https://fcm.googleapis.com:8443/x")).isFalse(); // porta fora da 443
        assertThat(producao.valido("ftp://fcm.googleapis.com/x")).isFalse();
        assertThat(producao.valido("nao e url")).isFalse();
        assertThat(producao.valido(null)).isFalse();
    }

    @Test
    void localSoComAFlagDeTeste() {
        ValidadorDeEndpoint teste = new ValidadorDeEndpoint(true);
        assertThat(teste.valido("http://localhost:8089/push/abc")).isTrue();
        assertThat(teste.valido("https://169.254.169.254/x")).isFalse();
    }
}
