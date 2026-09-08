package com.agroclima.api.business.estacao;

import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Espelha app/db/queries/estacao_proxima.py -- Principio IV: consulta espacial nativa
 * (operador KNN "<->" pra explorar o indice GiST), nunca Haversine em codigo de aplicacao.
 */
public interface EstacaoInmetRepository extends JpaRepository<EstacaoInmet, String> {

    @Query(value = "SELECT e.codigo AS codigo, e.nome AS nome, "
            + "ST_Distance(CAST(e.posicao AS geography), CAST(:centroide AS geography)) / 1000 AS distanciaKm, "
            + "ST_Y(e.posicao) AS latitude, ST_X(e.posicao) AS longitude "
            + "FROM estacoes_inmet e "
            + "ORDER BY e.posicao <-> :centroide "
            + "LIMIT :limite",
            nativeQuery = true)
    List<EstacaoProximaProjecao> buscarMaisProximas(@Param("centroide") Point centroide, @Param("limite") int limite);
}
