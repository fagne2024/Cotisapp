package com.cotisapp.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notification_etat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notif_etat",
                columnNames = {"utilisateur_id", "organisation_id", "cle_notification"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEtat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "utilisateur_id", nullable = false)
    private Long utilisateurId;

    @Column(name = "cle_notification", nullable = false, length = 120)
    private String cleNotification;

    @Column(nullable = false)
    @Builder.Default
    private Boolean lu = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean masque = false;

    @Column(name = "date_modification", nullable = false)
    @Builder.Default
    private LocalDateTime dateModification = LocalDateTime.now();
}
