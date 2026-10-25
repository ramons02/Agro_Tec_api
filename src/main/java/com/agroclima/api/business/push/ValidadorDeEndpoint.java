package com.agroclima.api.business.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * O servidor envia um POST para o endpoint que o navegador informa. Sem filtro, qualquer pessoa logada faria a API
 * chamar um endereço interno (SSRF). Só entram endereços https dos serviços de push dos navegadores.
 */
@Component
public class ValidadorDeEndpoint {

    private static final List<String> DOMINIOS_PERMITIDOS = List.of(
            "fcm.googleapis.com", // Chrome, Edge e Android
            "push.services.mozilla.com", // Firefox
            "notify.windows.com", // Edge no Windows
            "push.apple.com"); // Safari / iPhone

    private final boolean permitirLocal;

    public ValidadorDeEndpoint(@Value("${app.push.permitir-endpoint-local:false}") boolean permitirLocal) {
        this.permitirLocal = permitirLocal;
    }

    public boolean valido(String endpoint) {
        if (endpoint == null || endpoint.length() > 1000) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) {
            return false;
        }
        if (permitirLocal && ("localhost".equals(host) || "127.0.0.1".equals(host))) {
            return "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        }
        if (!"https".equals(uri.getScheme()) || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String minusculo = host.toLowerCase();
        return DOMINIOS_PERMITIDOS.stream().anyMatch(d -> minusculo.equals(d) || minusculo.endsWith("." + d));
    }
}
