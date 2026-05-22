package com.cotisapp.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Builder.Default
    private Boolean actif = true;

    @Column(length = 32)
    private String telephone;

    @Column(name = "telephone_normalise", length = 20)
    private String telephoneNormalise;

    @Column(name = "telephone_secondaire", length = 32)
    private String telephoneSecondaire;

    @Column(length = 255)
    private String adresse;

    @Column(name = "doit_changer_mot_de_passe", nullable = false)
    @Builder.Default
    private Boolean doitChangerMotDePasse = false;

    @Column(name = "totp_secret", length = 256)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private Boolean totpEnabled = false;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
