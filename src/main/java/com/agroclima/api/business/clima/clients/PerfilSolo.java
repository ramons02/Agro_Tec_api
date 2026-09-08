package com.agroclima.api.business.clima.clients;

public record PerfilSolo(
        double fracaoArgilaPct,
        double fracaoAreiaPct,
        double fracaoSiltePct,
        double materiaOrganicaPct,
        double densidadeSoloGCm3) {}
