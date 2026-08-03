package com.karamba121.backend.features.identity;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.karamba121.backend.config.IdentityHubProperties;

@Component
public class SmtpEmailVerificationSender implements EmailVerificationSender {

    private final JavaMailSender mailSender;
    private final IdentityHubProperties properties;

    public SmtpEmailVerificationSender(JavaMailSender mailSender, IdentityHubProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipient, String displayName, String verificationUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.registration().mailFrom());
        message.setTo(recipient);
        message.setSubject("Confirme seu e-mail no Identity Hub");
        message.setText("""
                Olá, %s.

                Confirme seu endereço de e-mail para ativar a conta no Identity Hub:
                %s

                Se você não solicitou este cadastro, ignore esta mensagem.
                """.formatted(displayName, verificationUrl));
        mailSender.send(message);
    }
}
