package com.agroclima.api.business.talhao;

import java.util.UUID;

/** Publicado quando um talhao e criado ou tem a geometria trocada: o perfil do solo deve ser buscado. */
public record TalhaoPrecisaDeSolo(UUID talhaoId) {}
