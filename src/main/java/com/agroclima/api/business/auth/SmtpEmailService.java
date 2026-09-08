package com.agroclima.api.business.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String remetente;

    public SmtpEmailService(JavaMailSender mailSender, String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviarRecuperacaoSenha(String destinatario, String link) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatario);
        mensagem.setSubject("Redefinição de senha — AgroClima Pará");
        mensagem.setText(
                "Clique no link abaixo para redefinir sua senha (válido por 1 hora):\n\n" + link);
        mailSender.send(mensagem);
    }
}
