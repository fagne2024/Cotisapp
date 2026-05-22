package com.cotisapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActivationEmailService {

    public static final String MDP_MEMBRE_INITIAL = "Passer123";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${cotisapp.mail.from:noreply@cotisapp.sn}")
    private String fromAddress;

    public void envoyerMotDePasseMembre(String email, String prenom, String motDePasseClair) {
        String corps = """
                Bonjour %s,

                Votre compte membre CotisApp a été créé.

                Mot de passe temporaire : %s

                À la première connexion (avec votre numéro de téléphone), vous devrez choisir un nouveau mot de passe.

                Cordialement,
                CotisApp
                """
                .formatted(prenom != null ? prenom : "", motDePasseClair)
                .trim();

        if (mailSender == null) {
            log.info(
                    "Email activation (SMTP non configuré) → {} | mot de passe initial : {}",
                    email,
                    motDePasseClair);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject("CotisApp — Votre accès membre");
            message.setText(corps);
            mailSender.send(message);
            log.info("Email d'activation envoyé à {}", email);
        } catch (Exception ex) {
            log.warn("Échec envoi email à {} : {} — mot de passe initial : {}", email, ex.getMessage(), motDePasseClair);
        }
    }
}
