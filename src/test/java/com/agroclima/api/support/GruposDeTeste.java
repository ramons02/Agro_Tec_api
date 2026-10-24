package com.agroclima.api.support;

import com.agroclima.api.business.auth.Usuario;
import com.agroclima.api.business.auth.UsuarioRepository;
import com.agroclima.api.business.grupo.Grupo;
import com.agroclima.api.business.grupo.GrupoRepository;

import java.time.Instant;

/** Apoio dos testes de integracao: cria grupos e coloca contas neles (spec 024). */
public final class GruposDeTeste {

    private GruposDeTeste() {}

    /** Cria o grupo (codigo do link derivado do nome, para ser unico) e coloca o admin nele. */
    public static Grupo criarGrupo(
            GrupoRepository grupos, UsuarioRepository usuarios, String nome, Usuario admin) {
        String codigo = Integer.toHexString(nome.hashCode()) + "-" + admin.getId();
        Grupo grupo = grupos.save(new Grupo(nome, admin.getId(), codigo, Instant.now()));
        admin.definirGrupo(grupo.getId());
        usuarios.save(admin);
        return grupo;
    }
}
