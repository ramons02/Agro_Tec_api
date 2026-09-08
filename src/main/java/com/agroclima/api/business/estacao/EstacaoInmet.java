package com.agroclima.api.business.estacao;

import com.agroclima.api.core.base.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "estacoes_inmet")
public class EstacaoInmet extends BaseModel<String> {

    @Id
    @Column(length = 10)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 2)
    private String estado;

    @Column(nullable = false)
    private Point posicao;

    protected EstacaoInmet() {}

    public EstacaoInmet(String codigo, String nome, String estado, Point posicao) {
        this.codigo = codigo;
        this.nome = nome;
        this.estado = estado;
        this.posicao = posicao;
    }

    @Override
    public String getId() {
        return codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getEstado() {
        return estado;
    }

    public Point getPosicao() {
        return posicao;
    }
}
