package com.agroclima.api.core.cache;

import com.agroclima.api.core.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unitário do cache Redis (spec 018): hit/miss contra um Redis real (mesmo padrão de
 * IntegrationTestBase pra Postgres -- requer REDIS_URL, disponível no CI via service
 * container; localmente sem Docker os dois testes de hit/miss não rodam sem um Redis à
 * mão) e falha de conexão/ausência de configuração, que não dependem de infra nenhuma.
 */
class CacheRedisServiceTest {

    private static final String REDIS_URL = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");

    private CacheRedisService servico(String url) {
        AppProperties propriedades =
                new AppProperties(null, null, null, null, null, null, null, new AppProperties.Redis(url));
        return new CacheRedisService(propriedades);
    }

    @Test
    void semRedisUrlNuncaTentaConectarEDegradaSemErro() {
        CacheRedisService cache = servico("");

        assertThatCode(() -> {
            assertThat(cache.obter("qualquer-chave")).isEmpty();
            cache.definir("qualquer-chave", "valor", 900);
        }).doesNotThrowAnyException();
    }

    @Test
    void conexaoIndisponivelDegradaSemErro() {
        // Porta baixa nenhum Redis usa -- conexão recusada imediatamente, sem precisar de rede real.
        CacheRedisService cache = servico("redis://localhost:1");

        assertThatCode(() -> {
            assertThat(cache.obter("qualquer-chave")).isEmpty();
            cache.definir("qualquer-chave", "valor", 900);
        }).doesNotThrowAnyException();
    }

    @Test
    void hitDepoisDeDefinirEMissParaChaveInexistente() {
        CacheRedisService cache = servico(REDIS_URL);
        String chave = "teste:" + UUID.randomUUID();

        assertThat(cache.obter(chave)).isEmpty();

        cache.definir(chave, "valor-de-teste", 60);

        assertThat(cache.obter(chave)).contains("valor-de-teste");
        assertThat(cache.obter("chave-que-nunca-existiu:" + UUID.randomUUID())).isEmpty();

        cache.destroy();
    }
}
