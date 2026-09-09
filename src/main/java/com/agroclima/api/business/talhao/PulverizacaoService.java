package com.agroclima.api.business.talhao;

import com.agroclima.api.business.clima.ClimaTempoRealService;
import com.agroclima.api.core.calculos.ClassificacaoPulverizacao;
import com.agroclima.api.core.calculos.PsicrometriaCalculos;
import com.agroclima.api.core.calculos.PulverizacaoCalculos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extraído de TalhaoController (era um método privado) porque o job de alerta via Telegram
 * (spec 017) precisa da mesma classificação usada pelo endpoint /pulverizacao -- nunca
 * duplicar a regra de negócio em dois lugares.
 */
@Service
public class PulverizacaoService {

    private final ClimaTempoRealService climaTempoRealService;

    public PulverizacaoService(ClimaTempoRealService climaTempoRealService) {
        this.climaTempoRealService = climaTempoRealService;
    }

    public record ResultadoPulverizacao(
            ClassificacaoPulverizacao classificacaoFinal,
            List<String> motivosBloqueio,
            double ventoKmh,
            Double rajadaKmh,
            Double deltaTC,
            Object fonteDados) {}

    public Optional<ResultadoPulverizacao> classificarAtual(Talhao talhao) {
        return climaTempoRealService.obterClimaAtual(talhao).map(resultado -> {
            ClassificacaoPulverizacao classificacaoVento =
                    PulverizacaoCalculos.classificarPulverizacao(resultado.ventoKmh(), resultado.rajadaKmh());

            Double deltaT = null;
            ClassificacaoPulverizacao classificacaoDeltaT = ClassificacaoPulverizacao.FAVORAVEL;
            if (resultado.temperaturaC() != null && resultado.umidadePct() != null) {
                deltaT = PsicrometriaCalculos.calcularDeltaT(resultado.temperaturaC(), resultado.umidadePct());
                classificacaoDeltaT = PulverizacaoCalculos.classificarDeltaT(deltaT);
            }

            List<String> motivosBloqueio = new ArrayList<>();
            if (classificacaoVento != ClassificacaoPulverizacao.FAVORAVEL) {
                motivosBloqueio.add(classificacaoVento.name());
            }
            if (classificacaoDeltaT != ClassificacaoPulverizacao.FAVORAVEL) {
                motivosBloqueio.add(classificacaoDeltaT.name());
            }
            ClassificacaoPulverizacao classificacaoFinal = classificacaoVento != ClassificacaoPulverizacao.FAVORAVEL
                    ? classificacaoVento
                    : classificacaoDeltaT;

            return new ResultadoPulverizacao(
                    classificacaoFinal, motivosBloqueio, resultado.ventoKmh(), resultado.rajadaKmh(), deltaT,
                    resultado.fonteDados());
        });
    }
}
