package com.agroclima.api.business.auth;

public interface EmailService {

    void enviarRecuperacaoSenha(String destinatario, String link);
}
