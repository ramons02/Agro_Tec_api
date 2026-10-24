package com.agroclima.api.business.auth;

/**
 * RD009 — papeis de usuario mutuamente exclusivos. GESTOR_TECNOLOGIA e o "Admin" de um grupo; ADMIN_MASTER
 * (spec 024) e o dono da plataforma, acima dos grupos: o valor ja existe no banco (V18), mas nenhuma conta
 * o recebe nem nenhuma regra o usa antes da fase 2.
 */
public enum Papel {
    PRODUTOR_RURAL,
    AGRONOMO,
    GESTOR_TECNOLOGIA,
    ADMIN_MASTER
}
