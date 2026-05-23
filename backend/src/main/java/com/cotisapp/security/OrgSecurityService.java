package com.cotisapp.security;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.TypeProfilDroit;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.TypeProfilDroitRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service("orgSecurityService")
@RequiredArgsConstructor
public class OrgSecurityService {

    /** Actions ouvrant l'espace / menu « Gestion GIE ». */
    private static final Set<String> ACTIONS_GESTION_GIE = Set.of(
            "MEMBRE_LISTER",
            "SOLDE_ORG",
            "RAPPORT_COMPLET",
            "OP_COTISATION",
            "OP_EMPRUNT",
            "OP_REMBOURSEMENT",
            "OP_PENALITE",
            "OP_DEPENSE",
            "ADMIN_UTILISATEURS",
            "PARAM_REGLES");

    private final MembreRepository membreRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final TypeProfilDroitRepository typeProfilDroitRepository;

    public boolean belongsTo(Long orgId) {
        if (OrganisationContext.getRole() == Role.SUPERADMIN) {
            return true;
        }
        return Objects.equals(OrganisationContext.getOrganisationId(), orgId);
    }

    public boolean isMemberOf(Long orgId) {
        if (OrganisationContext.getRole() == Role.SUPERADMIN) {
            return true;
        }
        Long userId = OrganisationContext.getUserId();
        return userId != null && membreRepository.existsByUtilisateurIdAndOrganisationId(userId, orgId);
    }

    public boolean canAccessOrg(Long orgId) {
        Role role = OrganisationContext.getRole();
        if (role == Role.SUPERADMIN) {
            return true;
        }
        return belongsTo(orgId) || isMemberOf(orgId);
    }

    /**
     * Accès aux écrans de gestion du GIE (dashboard, membres, opérations…).
     * Admin GIE : oui. Membre de bureau : au moins une action de gestion active sur son profil.
     */
    /** Activation paiement mobile money « Mon compte » — admin GIE uniquement. */
    public boolean peutConfigurerPaiementMobileOrg(Long orgId) {
        Role role = OrganisationContext.getRole();
        if (role == Role.SUPERADMIN) {
            return true;
        }
        return role == Role.ADMIN_GIE && Objects.equals(OrganisationContext.getOrganisationId(), orgId);
    }

    public boolean peutGestionOrg(Long orgId) {
        Role role = OrganisationContext.getRole();
        if (role == Role.SUPERADMIN) {
            return true;
        }
        if (!Objects.equals(OrganisationContext.getOrganisationId(), orgId)) {
            return false;
        }
        if (role == Role.ADMIN_GIE) {
            return true;
        }
        if (role == Role.MEMBRE) {
            return ACTIONS_GESTION_GIE.stream().anyMatch(code -> peutActionOrg(orgId, code));
        }
        return false;
    }

    /**
     * Vérifie une action du catalogue de droits pour l'utilisateur courant sur cette organisation.
     */
    public boolean peutActionOrg(Long orgId, String actionCode) {
        if (actionCode == null || actionCode.isBlank()) {
            return false;
        }
        Role role = OrganisationContext.getRole();
        if (role == Role.SUPERADMIN) {
            return true;
        }
        if (!Objects.equals(OrganisationContext.getOrganisationId(), orgId)) {
            return false;
        }
        if (role == Role.ADMIN_GIE) {
            return true;
        }
        if (role == Role.MEMBRE) {
            NiveauDroit niveau = niveauDroitCourant(orgId, actionCode.trim());
            return niveau != null && niveau != NiveauDroit.NO;
        }
        return false;
    }

    private NiveauDroit niveauDroitCourant(Long orgId, String actionCode) {
        Long typeProfilId = resoudreTypeProfilIdCourant(orgId);
        if (typeProfilId == null) {
            return NiveauDroit.NO;
        }
        Map<String, NiveauDroit> niveaux = typeProfilDroitRepository.findByTypeProfilIdOrderByActionCodeAsc(typeProfilId).stream()
                .collect(Collectors.toMap(TypeProfilDroit::getActionCode, TypeProfilDroit::getNiveau, (a, b) -> a));
        return niveaux.getOrDefault(actionCode, NiveauDroit.NO);
    }

    private Long resoudreTypeProfilIdCourant(Long orgId) {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            return null;
        }
        Role role = OrganisationContext.getRole();
        if (role == null) {
            return null;
        }
        Optional<UtilisateurRole> ur = utilisateurRoleRepository
                .findFirstByUtilisateurIdAndRoleAndOrganisationIdOrderByIdAsc(userId, role, orgId);
        if (ur.isEmpty()) {
            ur = utilisateurRoleRepository.findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(userId, orgId);
        }
        return ur.map(UtilisateurRole::getTypeProfilId).orElse(null);
    }

    /**
     * Identifiant membre pour « Mon compte » : priorité au contexte (JWT / rôle sélectionné), sinon fiche
     * {@link Membre#getUtilisateurId()} pour cette organisation (cas fréquent : admin GIE sans {@code membre_id}
     * sur la ligne {@code utilisateur_role} ADMIN).
     */
    public Optional<Long> resolveMembreIdPourMonCompte(Long orgId) {
        Long fromCtx = OrganisationContext.getMembreId();
        if (OrganisationContext.getRole() == Role.MEMBRE) {
            if (fromCtx == null || !Objects.equals(OrganisationContext.getOrganisationId(), orgId)) {
                return Optional.empty();
            }
            return membreRepository
                    .findByIdAndOrganisationId(fromCtx, orgId)
                    .filter(m -> Objects.equals(m.getUtilisateurId(), OrganisationContext.getUserId()))
                    .map(Membre::getId);
        }
        if (fromCtx != null) {
            return membreRepository.findByIdAndOrganisationId(fromCtx, orgId).map(Membre::getId);
        }
        Long uid = OrganisationContext.getUserId();
        if (uid == null) {
            return Optional.empty();
        }
        return membreRepository.findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(uid, orgId).map(Membre::getId);
    }

    /**
     * Fiche « Mon compte » : membre de l'org, ou admin GIE de l'org lié à une fiche membre (directement ou via
     * {@code utilisateur_id} sur {@code membre}).
     */
    public boolean peutConsulterMonCompte(Long orgId) {
        if (!belongsTo(orgId)) {
            return false;
        }
        Role role = OrganisationContext.getRole();
        if (role == Role.MEMBRE) {
            return resolveMembreIdPourMonCompte(orgId).isPresent();
        }
        if (role == Role.ADMIN_GIE) {
            return resolveMembreIdPourMonCompte(orgId).isPresent();
        }
        return false;
    }

    /** Admin GIE de l'org ou membre consultant sa propre fiche. */
    public boolean canViewMembre(Long orgId, Long membreId) {
        if (OrganisationContext.getRole() == Role.SUPERADMIN) {
            return true;
        }
        if (!Objects.equals(OrganisationContext.getOrganisationId(), orgId)) {
            return false;
        }
        if (OrganisationContext.getRole() == Role.ADMIN_GIE) {
            return true;
        }
        if (OrganisationContext.getRole() == Role.MEMBRE) {
            if (Objects.equals(OrganisationContext.getMembreId(), membreId)) {
                return true;
            }
            return peutActionOrg(orgId, "MEMBRE_LISTER");
        }
        return false;
    }

    /** Membre du bureau (poste autre que SIMPLE) pour l'organisation courante. */
    public boolean estMembreBureauCourant(Long orgId) {
        if (OrganisationContext.getRole() != Role.MEMBRE) {
            return false;
        }
        Long membreId = OrganisationContext.getMembreId();
        if (membreId == null) {
            return false;
        }
        return membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .map(m -> m.getPoste() != null && m.getPoste() != PosteMembre.SIMPLE)
                .orElse(false);
    }
}
