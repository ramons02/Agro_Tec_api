package com.agroclima.api.core.calculos;

/** Aproximacao de Stull (2011) para bulbo umido, usada no Delta T da pulverizacao (RN021/RN022). */
public final class PsicrometriaCalculos {

    private PsicrometriaCalculos() {}

    public static double calcularBulboUmido(double temperaturaSecaC, double umidadeRelativaPct) {
        double t = temperaturaSecaC;
        double rh = umidadeRelativaPct;
        return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659))
                + Math.atan(t + rh)
                - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh)
                - 4.686035;
    }

    public static double calcularDeltaT(double temperaturaSecaC, double umidadeRelativaPct) {
        return temperaturaSecaC - calcularBulboUmido(temperaturaSecaC, umidadeRelativaPct);
    }
}
