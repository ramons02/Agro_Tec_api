package com.agroclima.api.business.talhao;

import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Queries espaciais nativas (ST_Overlaps/ST_Intersection/ST_Area::geography) -- nao
 * expressaveis em JPQL/Criteria, igual ao Python (Principio IV: nunca aproximacao
 * planar/calculo em aplicacao, sempre operador nativo do PostGIS).
 */
public interface TalhaoRepository extends JpaRepository<Talhao, UUID> {

    Page<Talhao> findByPropriedadeIdIn(List<UUID> propriedadeIds, Pageable pageable);

    Page<Talhao> findByPropriedadeId(UUID propriedadeId, Pageable pageable);

    /** RN015 -- sobreposicao dentro da mesma propriedade, area da intersecao > 10m2, bloqueia. */
    @Query(value = "SELECT t.nome FROM talhoes t "
            + "WHERE t.propriedade_id = :propriedadeId "
            + "AND ST_Overlaps(t.geometria, :geometria) "
            + "AND ST_Area(CAST(ST_Intersection(t.geometria, :geometria) AS geography)) > 10.0",
            nativeQuery = true)
    List<String> buscarSobreposicaoMesmaPropriedade(
            @Param("propriedadeId") UUID propriedadeId, @Param("geometria") Geometry geometria);

    /** RN015 -- sobreposicao com talhao de OUTRA propriedade, permitida com aviso, nunca bloqueia. */
    @Query(value = "SELECT t.nome FROM talhoes t "
            + "WHERE t.propriedade_id <> :propriedadeId "
            + "AND ST_Overlaps(t.geometria, :geometria) "
            + "AND ST_Area(CAST(ST_Intersection(t.geometria, :geometria) AS geography)) > 10.0",
            nativeQuery = true)
    List<String> buscarSobreposicaoOutraPropriedade(
            @Param("propriedadeId") UUID propriedadeId, @Param("geometria") Geometry geometria);

    /** Area geodesica real (::geography), nunca aproximacao planar em graus. */
    @Query(value = "SELECT ST_Area(CAST(:geometria AS geography)) / 10000.0", nativeQuery = true)
    double calcularAreaHa(@Param("geometria") Geometry geometria);
}
