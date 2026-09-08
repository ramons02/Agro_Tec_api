package com.agroclima.api.core.security;

import com.agroclima.api.business.auth.Papel;

import java.util.UUID;

/** Principal autenticado -- espelha o modelo UsuarioAutenticado de app/core/security.py. */
public record UsuarioAutenticado(UUID id, Papel papel) {}
