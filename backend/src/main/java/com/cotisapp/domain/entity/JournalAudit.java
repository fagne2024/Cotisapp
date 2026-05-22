package com.cotisapp.domain.entity;

import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeEvenementJournal;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "journal_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "utilisateur_id")
    private Long utilisateurId;

    @Column(name = "utilisateur_email")
    private String utilisateurEmail;

    @Column(name = "utilisateur_nom")
    private String utilisateurNom;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private Role role;

    @Column(name = "membre_id")
    private Long membreId;

    @Column(nullable = false, length = 100)
    private String action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_evenement", nullable = false, length = 40)
    private TypeEvenementJournal typeEvenement;

    @Column(name = "module_code", length = 80)
    private String moduleCode;

    @Column(name = "module_libelle", length = 200)
    private String moduleLibelle;

    @Column(name = "route_path", length = 500)
    private String routePath;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean succes = true;

    @Column(name = "date_creation", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime dateCreation = LocalDateTime.now();
}
