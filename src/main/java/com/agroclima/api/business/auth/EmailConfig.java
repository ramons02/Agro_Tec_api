package com.agroclima.api.business.auth;

import com.agroclima.api.core.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Fabrica EmailService igual a obter_email_service() de app/services/email_service.py. */
@Configuration
public class EmailConfig {

    private final AppProperties appProperties;

    public EmailConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** Chave da API do Brevo. Tem prioridade sobre o SMTP: o Render gratis bloqueia as portas SMTP. */
    @Value("${app.email.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${app.email.from:}")
    private String remetenteApi;

    @Value("${app.email.from-name:AgroClima Pará}")
    private String nomeRemetenteApi;

    @Value("${app.email.brevo-base-url:https://api.brevo.com}")
    private String brevoBaseUrl;

    @Bean
    public EmailService emailService() {
        if (naoEstaVazio(brevoApiKey) && naoEstaVazio(remetenteApi)) {
            return new BrevoEmailService(brevoBaseUrl, brevoApiKey, remetenteApi, nomeRemetenteApi);
        }

        AppProperties.Smtp smtp = appProperties.smtp();
        boolean configurado = smtp != null
                && naoEstaVazio(smtp.host())
                && naoEstaVazio(smtp.user())
                && naoEstaVazio(smtp.password());

        if (!configurado) {
            return new LogEmailService();
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtp.host());
        mailSender.setPort(smtp.port());
        mailSender.setUsername(smtp.user());
        mailSender.setPassword(smtp.password());
        mailSender.getJavaMailProperties().put("mail.smtp.auth", "true");
        mailSender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");

        String remetente = naoEstaVazio(smtp.from()) ? smtp.from() : smtp.user();
        return new SmtpEmailService(mailSender, remetente);
    }

    private static boolean naoEstaVazio(String valor) {
        return valor != null && !valor.isBlank();
    }
}
