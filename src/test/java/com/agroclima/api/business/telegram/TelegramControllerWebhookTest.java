package com.agroclima.api.business.telegram;

import com.agroclima.api.core.config.AppProperties;
import com.agroclima.api.core.response.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TelegramControllerWebhookTest {

    private final TelegramService service = mock(TelegramService.class);
    private final JsonNode payload = new ObjectMapper().createObjectNode();

    private TelegramController controllerComSegredo(String segredo) {
        AppProperties props = new AppProperties(
                null, null, null, null, null,
                new AppProperties.Telegram("bot", "bot_user", "http://x", segredo));
        return new TelegramController(service, props);
    }

    @Test
    void segredoCorretoProcessaOWebhook() {
        Map<String, Boolean> resposta = controllerComSegredo("segredo-certo").webhook("segredo-certo", payload);

        assertEquals(Map.of("ok", true), resposta);
        verify(service).processarWebhook(payload);
    }

    @Test
    void segredoErradoEhRecusadoSemProcessar() {
        AppException ex = assertThrows(AppException.class,
                () -> controllerComSegredo("segredo-certo").webhook("outro", payload));

        assertEquals(403, ex.getCodigo());
        verify(service, never()).processarWebhook(any());
    }

    @Test
    void headerAusenteEhRecusado() {
        assertThrows(AppException.class, () -> controllerComSegredo("segredo-certo").webhook(null, payload));
        verify(service, never()).processarWebhook(any());
    }

    @Test
    void semSegredoConfiguradoRejeitaTudoMesmoComHeaderVazio() {
        assertThrows(AppException.class, () -> controllerComSegredo("").webhook("", payload));
        assertThrows(AppException.class, () -> controllerComSegredo(null).webhook(null, payload));
        verify(service, never()).processarWebhook(any());
    }
}
