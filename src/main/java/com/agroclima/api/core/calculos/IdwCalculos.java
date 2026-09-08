package com.agroclima.api.core.calculos;

import java.util.List;

/** Interpolacao IDW (distancia inversa ao quadrado) -- Escopo V3 §1B. Porte literal de idw.py. */
public final class IdwCalculos {

    private IdwCalculos() {}

    public record ValorDistancia(double valor, double distanciaKm) {}

    public static double interpolar(List<ValorDistancia> valoresEDistancias) {
        if (valoresEDistancias.isEmpty()) {
            throw new IllegalArgumentException("Lista de valores/distancias nao pode ser vazia.");
        }
        for (ValorDistancia vd : valoresEDistancias) {
            if (vd.distanciaKm() == 0.0) {
                return vd.valor();
            }
        }
        double numerador = 0.0;
        double denominador = 0.0;
        for (ValorDistancia vd : valoresEDistancias) {
            double peso = 1.0 / (vd.distanciaKm() * vd.distanciaKm());
            numerador += vd.valor() * peso;
            denominador += peso;
        }
        return numerador / denominador;
    }
}
