package com.agroclima.api.business.estacao;

/**
 * Projeção da query KNN -- espelha EstacaoProximaResultado de app/db/queries/estacao_proxima.py,
 * mas com o campo corrigido: o Python chamava esse campo de "municipio" mas populava com o
 * nome da estação (bug latente identificado no porte) -- aqui já nasce nomeado certo.
 */
public interface EstacaoProximaProjecao {

    String getCodigo();

    String getNome();

    Double getDistanciaKm();

    Double getLatitude();

    Double getLongitude();
}
