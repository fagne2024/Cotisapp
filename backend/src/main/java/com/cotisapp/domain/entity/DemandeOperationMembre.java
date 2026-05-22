package com.cotisapp.domain.entity;

import com.cotisapp.domain.OrganisationScoped;
import com.cotisapp.domain.enums.DemandeOperationStatut;
import com.cotisapp.domain.enums.DemandeOperationType;
import com.cotisapp.domain.enums.ModePaiement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "demande_operation_membre")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeOperationMembre implements OrganisationScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "membre_id", nullable = false)
    private Long membreId;

    @Column(name = "demandeur_utilisateur_id", nullable = false)
    private Long demandeurUtilisateurId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type_demande", nullable = false, length = 40)
    private DemandeOperationType typeDemande;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DemandeOperationStatut statut = DemandeOperationStatut.EN_ATTENTE;

    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "emprunt_id")
    private Long empruntId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode_paiement", length = 30)
    private ModePaiement modePaiement;

    @Column(name = "reference_paiement", length = 120)
    private String referencePaiement;

    @Column(name = "libelle_resume", length = 500)
    private String libelleResume;

    @Column(name = "date_demande", nullable = false)
    private LocalDateTime dateDemande;

    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    @Column(name = "validateur_utilisateur_id")
    private Long validateurUtilisateurId;

    @Column(name = "motif_refus", length = 500)
    private String motifRefus;

    @Column(name = "operation_id")
    private Long operationId;
}
