package com.agroclima.api.core.response;

/**
 * Mecanismo unico de erro de dominio (401/403/404/409/422 etc, decidido por call-site) --
 * espelha AppError de app/core/response.py.
 */
public class AppException extends RuntimeException {

    private final int codigo;
    private final Object detalhes;

    public AppException(int codigo, String mensagem) {
        this(codigo, mensagem, null);
    }

    public AppException(int codigo, String mensagem, Object detalhes) {
        super(mensagem);
        this.codigo = codigo;
        this.detalhes = detalhes;
    }

    public int getCodigo() {
        return codigo;
    }

    public Object getDetalhes() {
        return detalhes;
    }
}
