package com.agroclima.api.core.cache;

import com.agroclima.api.core.config.AppProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache de curto prazo em Redis (spec 018, RF038/RNF019). Conexão lazy (só na primeira
 * chamada) e nunca propaga falha -- REDIS_URL ausente, conexão recusada ou timeout viram
 * sempre um cache miss silencioso (aviso no log), nunca uma exceção pro chamador.
 */
@Service
public class CacheRedisService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CacheRedisService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private final String url;
    private RedisClient client;
    private StatefulRedisConnection<String, String> conexao;
    private boolean conexaoFalhou;

    public CacheRedisService(AppProperties appProperties) {
        this.url = appProperties.redis().url();
    }

    public Optional<String> obter(String chave) {
        RedisCommands<String, String> comandos = comandos();
        if (comandos == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(comandos.get(chave));
        } catch (RuntimeException ex) {
            log.warn("Falha ao ler do cache Redis (chave={}): {}", chave, ex.getMessage());
            return Optional.empty();
        }
    }

    public void definir(String chave, String valor, long ttlSegundos) {
        RedisCommands<String, String> comandos = comandos();
        if (comandos == null) {
            return;
        }
        try {
            comandos.setex(chave, ttlSegundos, valor);
        } catch (RuntimeException ex) {
            log.warn("Falha ao gravar no cache Redis (chave={}): {}", chave, ex.getMessage());
        }
    }

    private synchronized RedisCommands<String, String> comandos() {
        if (url == null || url.isBlank() || conexaoFalhou) {
            return null;
        }
        if (conexao != null) {
            return conexao.sync();
        }
        try {
            RedisClient novoClient = RedisClient.create(url);
            novoClient.setOptions(ClientOptions.builder()
                    .socketOptions(SocketOptions.builder().connectTimeout(TIMEOUT).build())
                    .timeoutOptions(TimeoutOptions.builder().fixedTimeout(TIMEOUT).build())
                    .build());
            conexao = novoClient.connect();
            client = novoClient;
            return conexao.sync();
        } catch (RuntimeException ex) {
            log.warn("Redis indisponível, consultas de estação seguem sem cache: {}", ex.getMessage());
            conexaoFalhou = true;
            return null;
        }
    }

    @Override
    public void destroy() {
        if (conexao != null) {
            conexao.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
}
