package com.karamba121.backend.features.identity;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.karamba121.backend.config.IdentityHubProperties;

@Component
public class SmtpPasswordRecoverySender implements PasswordRecoverySender {

    private final JavaMailSender mailSender;
    private final IdentityHubProperties properties;

    public SmtpPasswordRecoverySender(JavaMailSender mailSender, IdentityHubProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipient, String displayName, String recoveryUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.registration().mailFrom());
        message.setTo(recipient);
        message.setSubject("Redefina sua senha no Identity Hub");
        message.setText("""
                Olá, %s.

                Use o link abaixo para definir uma nova senha no Identity Hub:
                %s

                O link expira em breve e só pode ser utilizado uma vez. Se você não solicitou a recuperação, ignore esta mensagem.
                """.formatted(displayName, recoveryUrl));
        mailSender.send(message);
    }
}
