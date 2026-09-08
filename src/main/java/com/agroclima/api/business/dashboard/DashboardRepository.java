package com.agroclima.api.business.dashboard;

import com.agroclima.api.business.talhao.Talhao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * So queries de leitura entre talhoes/propriedades/balanco_hidrico_diario -- espelha
 * _query_talhoes_filtrada() de app/api/v1/endpoints/dashboard.py. LATERAL join pro
 * "ultimo balanco por talhao" (equivalente ao ROW_NUMBER() OVER(...) do Python).
 */
public interface DashboardRepository extends Repository<Talhao, UUID> {

    @Query(value = "SELECT t.id AS talhaoId, t.nome AS nome, p.nome AS propriedadeNome, "
            + "t.area_ha AS areaHa, t.tipo_solo AS tipoSolo, b.status_plantio AS statusPlantio, "
            + "b.armazenamento_mm AS armazenamentoMm, t.capacidade_agua_disponivel_mm AS cadMm "
            + "FROM talhoes t JOIN propriedades p ON p.id = t.propriedade_id "
            + "LEFT JOIN LATERAL (SELECT status_plantio, armazenamento_mm FROM balanco_hidrico_diario bhd "
            + "  WHERE bhd.talhao_id = t.id ORDER BY bhd.data DESC LIMIT 1) b ON true "
            + "WHERE t.propriedade_id IN (:propriedadeIds) "
            + "AND (:propriedadeIdFiltro IS NULL OR t.propriedade_id = :propriedadeIdFiltro) "
            + "AND (:status IS NULL OR b.status_plantio = CAST(:status AS status_plantio))",
            countQuery = "SELECT count(*) FROM talhoes t "
                    + "LEFT JOIN LATERAL (SELECT status_plantio FROM balanco_hidrico_diario bhd "
                    + "  WHERE bhd.talhao_id = t.id ORDER BY bhd.data DESC LIMIT 1) b ON true "
                    + "WHERE t.propriedade_id IN (:propriedadeIds) "
                    + "AND (:propriedadeIdFiltro IS NULL OR t.propriedade_id = :propriedadeIdFiltro) "
                    + "AND (:status IS NULL OR b.status_plantio = CAST(:status AS status_plantio))",
            nativeQuery = true)
    Page<DashboardItemProjecao> buscarEscopado(
            @Param("propriedadeIds") List<UUID> propriedadeIds,
            @Param("propriedadeIdFiltro") UUID propriedadeIdFiltro,
            @Param("status") String status,
            Pageable pageable);

    @Query(value = "SELECT t.id AS talhaoId, t.nome AS nome, p.nome AS propriedadeNome, "
            + "t.area_ha AS areaHa, t.tipo_solo AS tipoSolo, b.status_plantio AS statusPlantio, "
            + "b.armazenamento_mm AS armazenamentoMm, t.capacidade_agua_disponivel_mm AS cadMm "
            + "FROM talhoes t JOIN propriedades p ON p.id = t.propriedade_id "
            + "LEFT JOIN LATERAL (SELECT status_plantio, armazenamento_mm FROM balanco_hidrico_diario bhd "
            + "  WHERE bhd.talhao_id = t.id ORDER BY bhd.data DESC LIMIT 1) b ON true "
            + "WHERE (:propriedadeIdFiltro IS NULL OR t.propriedade_id = :propriedadeIdFiltro) "
            + "AND (:status IS NULL OR b.status_plantio = CAST(:status AS status_plantio))",
            countQuery = "SELECT count(*) FROM talhoes t "
                    + "LEFT JOIN LATERAL (SELECT status_plantio FROM balanco_hidrico_diario bhd "
                    + "  WHERE bhd.talhao_id = t.id ORDER BY bhd.data DESC LIMIT 1) b ON true "
                    + "WHERE (:propriedadeIdFiltro IS NULL OR t.propriedade_id = :propriedadeIdFiltro) "
                    + "AND (:status IS NULL OR b.status_plantio = CAST(:status AS status_plantio))",
            nativeQuery = true)
    Page<DashboardItemProjecao> buscarIrrestrito(
            @Param("propriedadeIdFiltro") UUID propriedadeIdFiltro, @Param("status") String status, Pageable pageable);
}
