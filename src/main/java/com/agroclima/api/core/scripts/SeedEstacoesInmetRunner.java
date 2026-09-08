package com.agroclima.api.core.scripts;

import com.agroclima.api.business.clima.clients.EstacaoInmetDto;
import com.agroclima.api.business.clima.clients.InmetClient;
import com.agroclima.api.business.estacao.EstacaoInmetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Espelha app/scripts/seed_estacoes_inmet.py -- roda so sob perfil "dev" e so quando
 * chamado explicitamente (java -jar app.jar --spring.profiles.active=dev seed-estacoes-inmet),
 * nunca em todo boot -- igual ao script Python (standalone, nao automatico).
 */
@Component
@Profile("dev")
public class SeedEstacoesInmetRunner implements CommandLineRunner {

    private static final String COMANDO = "seed-estacoes-inmet";
    private static final Logger log = LoggerFactory.getLogger(SeedEstacoesInmetRunner.class);

    private final InmetClient inmetClient;
    private final EstacaoInmetRepository estacaoInmetRepository;

    public SeedEstacoesInmetRunner(InmetClient inmetClient, EstacaoInmetRepository estacaoInmetRepository) {
        this.inmetClient = inmetClient;
        this.estacaoInmetRepository = estacaoInmetRepository;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0 || !COMANDO.equals(args[0])) {
            return;
        }
        List<EstacaoInmetDto> estacoes = inmetClient.buscarEstacoesPa();
        if (estacoes.isEmpty()) {
            log.warn("INMET não retornou nenhuma estação para o Pará -- nada foi sincronizado.");
            return;
        }
        for (EstacaoInmetDto estacao : estacoes) {
            estacaoInmetRepository.upsert(estacao.codigo(), estacao.nome(), estacao.latitude(), estacao.longitude());
        }
        log.info("{} estações do Pará sincronizadas.", estacoes.size());
    }
}
