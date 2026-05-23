package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.PosteMembre;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateMembreRequest {

    @NotBlank
    @Size(max = 100)
    private String prenom;

    @NotBlank
    @Size(max = 100)
    private String nom;

    @Size(max = 255)
    private String email;

    @Size(max = 30)
    private String telephone;

    private LocalDate dateAdhesion;

    @Size(max = 80)
    private String pieceIdentite;

    @NotNull
    private PosteMembre poste;

    @NotNull
    @Valid
    private ComptesMembreSelection comptes;

    /** Identifiants des modèles de comptes personnalisés à créer pour ce membre. */
    private List<Long> modelesCompteIds;

    /** Crée automatiquement le compte utilisateur MEMBRE (défaut : oui). */
    private Boolean creerCompteAcces;

    /** Envoie l’email avec le mot de passe initial Passer123. */
    private Boolean envoyerEmailActivation;

    private Long typeProfilId;

    /** Réservé à l'admin GIE (défaut : désactivé). */
    private Boolean paiementMobileActif;
}
