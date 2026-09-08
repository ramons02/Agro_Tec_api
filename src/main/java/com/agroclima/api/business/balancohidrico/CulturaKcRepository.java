package com.agroclima.api.business.balancohidrico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CulturaKcRepository extends JpaRepository<CulturaKc, Integer> {

    @Query("SELECT c FROM CulturaKc c WHERE c.cultura = :cultura AND :dae BETWEEN c.daeInicio AND c.daeFim")
    Optional<CulturaKc> findPorCulturaEDae(@Param("cultura") String cultura, @Param("dae") int dae);
}
