package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.entity.TypeProfil;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.ChangeMotDePasseRequest;
import com.cotisapp.dto.request.UpdateProfilRequest;
import com.cotisapp.dto.response.ProfilActiviteResponse;
import com.cotisapp.dto.response.ProfilResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.JournalAuditRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.TypeProfilRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.util.TelephoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProfilService {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final MembreRepository membreRepository;
    private final OrganisationRepository organisationRepository;
    private final TypeProfilRepository typeProfilRepository;
    private final JournalAuditRepository journalAuditRepository;
    private final PasswordEncoder passwordEncoder;
    private final JournalService journalService;

    @Transactional(readOnly = true)
    public ProfilResponse chargerProfilCourant() {
        ContexteProfil ctx = contexteProfil();
        verifierAccesOrganisation(ctx);
        return construireResponse(ctx);
    }

    @Transactional(readOnly = true)
    public List<ProfilActiviteResponse> activiteRecente() {
        ContexteProfil ctx = contexteProfil();
        verifierAccesOrganisation(ctx);
        Long userId = ctx.utilisateur().getId();
        List<com.cotisapp.domain.entity.JournalAudit> lignes;
        if (ctx.organisationId() != null && ctx.role() != Role.SUPERADMIN) {
            lignes = journalAuditRepository.findTop30ByUtilisateurIdAndOrganisationIdOrderByDateCreationDesc(
                    userId, ctx.organisationId());
        } else if (ctx.organisationId() != null) {
            lignes = journalAuditRepository.findTop30ByUtilisateurIdAndOrganisationIdOrderByDateCreationDesc(
                    userId, ctx.organisationId());
        } else {
            lignes = journalAuditRepository.findTop30ByUtilisateurIdOrderByDateCreationDesc(userId);
        }
        return lignes.stream().map(this::toActivite).toList();
    }

    @Transactional
    public ProfilResponse mettreAJour(UpdateProfilRequest request) {
        ContexteProfil ctx = contexteProfil();
        verifierAccesOrganisation(ctx);
        Utilisateur u = ctx.utilisateur();

        if (ctx.role() == Role.SUPERADMIN || ctx.role() == Role.ADMIN_GIE) {
            String email = request.getEmail().trim().toLowerCase();
            if (!email.equalsIgnoreCase(u.getEmail())
                    && utilisateurRepository.existsByEmailAndIdNot(email, u.getId())) {
                throw new BusinessException("Cet email est déjà utilisé par un autre compte");
            }
            u.setEmail(email);
        }

        u.setPrenom(request.getPrenom().trim());
        u.setNom(request.getNom().trim());
        appliquerTelephoneUtilisateur(u, request.getTelephone());
        u.setTelephoneSecondaire(blankToNull(request.getTelephoneSecondaire()));
        u.setAdresse(blankToNull(request.getAdresse()));
        utilisateurRepository.save(u);

        if (ctx.membre() != null) {
            Membre m = ctx.membre();
            m.setPrenom(u.getPrenom());
            m.setNom(u.getNom());
            if (request.getTelephone() != null && !request.getTelephone().isBlank()) {
                m.setTelephone(request.getTelephone().trim());
                m.setTelephoneNormalise(TelephoneUtil.normaliser(request.getTelephone()));
            }
            if (ctx.role() == Role.ADMIN_GIE && request.getEmail() != null) {
                m.setEmail(request.getEmail().trim().toLowerCase());
            }
            membreRepository.save(m);
        }

        journalService.enregistrer(ctx.organisationId(), "PROFIL_MAJ", "Mise à jour du profil utilisateur");
        return construireResponse(contexteProfil());
    }

    @Transactional
    public void changerMotDePasse(ChangeMotDePasseRequest request) {
        if (!request.getNouveauMotDePasse().equals(request.getConfirmationMotDePasse())) {
            throw new BusinessException("La confirmation du mot de passe ne correspond pas");
        }
        ContexteProfil ctx = contexteProfil();
        Utilisateur u = ctx.utilisateur();
        if (!passwordEncoder.matches(request.getMotDePasseActuel(), u.getMotDePasse())) {
            throw new BusinessException("Mot de passe actuel incorrect");
        }
        u.setMotDePasse(passwordEncoder.encode(request.getNouveauMotDePasse()));
        utilisateurRepository.save(u);
        journalService.enregistrer(ctx.organisationId(), "MOT_DE_PASSE_MAJ", "Changement de mot de passe");
    }

    private ContexteProfil contexteProfil() {
        Long userId = OrganisationContext.getUserId();
        if (userId == null) {
            throw new BusinessException("Utilisateur non authentifié");
        }
        Utilisateur u = utilisateurRepository.findById(userId).orElseThrow(() -> new BusinessException("Compte introuvable"));
        Role role = OrganisationContext.getRole();
        Long orgId = OrganisationContext.getOrganisationId();
        Long membreId = OrganisationContext.getMembreId();

        UtilisateurRole ur = null;
        if (orgId != null && role != null) {
            ur = utilisateurRoleRepository
                    .findFirstByUtilisateurIdAndRoleAndOrganisationIdOrderByIdAsc(userId, role, orgId)
                    .orElse(null);
        }
        if (ur == null && orgId != null) {
            ur = utilisateurRoleRepository
                    .findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(userId, orgId)
                    .orElse(null);
        }
        if (ur == null && role == Role.SUPERADMIN) {
            ur = utilisateurRoleRepository
                    .findFirstByUtilisateurIdAndRoleOrderByIdAsc(userId, Role.SUPERADMIN)
                    .orElse(null);
        }

        Membre membre = null;
        if (membreId != null && orgId != null) {
            membre = membreRepository.findByIdAndOrganisationId(membreId, orgId).orElse(null);
        }

        TypeProfil typeProfil = null;
        if (ur != null && ur.getTypeProfilId() != null) {
            typeProfil = typeProfilRepository.findById(ur.getTypeProfilId()).orElse(null);
        }
        if (typeProfil == null && orgId != null && membre != null) {
            typeProfil = typeProfilRepository
                    .findFirstByOrganisationIdAndRoleAndPosteMembre(orgId, role, membre.getPoste())
                    .orElse(null);
        }
        if (typeProfil == null && role == Role.SUPERADMIN) {
            typeProfil = typeProfilRepository.findFirstByOrganisationIdIsNullAndCodeOrderByIdAsc("SUPERADMIN").orElse(null);
        }
        if (typeProfil == null && role == Role.ADMIN_GIE) {
            typeProfil = typeProfilRepository.findFirstByOrganisationIdIsNullAndCodeOrderByIdAsc("ADMIN_GIE").orElse(null);
        }

        return new ContexteProfil(u, role, orgId, membreId, ur, membre, typeProfil);
    }

    private void verifierAccesOrganisation(ContexteProfil ctx) {
        if (ctx.role() == Role.SUPERADMIN && ctx.organisationId() == null) {
            return;
        }
        if (ctx.organisationId() == null) {
            throw new BusinessException(
                    "Sélectionnez une organisation (en-tête X-Organisation-Id) pour afficher ce profil dans son contexte.");
        }
        if (ctx.role() != Role.SUPERADMIN && ctx.utilisateurRole() == null) {
            throw new BusinessException("Accès refusé pour cette organisation");
        }
    }

    private ProfilResponse construireResponse(ContexteProfil ctx) {
        Utilisateur u = ctx.utilisateur();
        String orgNom = ctx.organisationId() != null
                ? organisationRepository.findById(ctx.organisationId()).map(Organisation::getNom).orElse(null)
                : null;

        TypeProfil tp = ctx.typeProfil();
        String typeLibelle = tp != null ? tp.getLibelle() : libelleRole(ctx.role());
        String typeCode = tp != null ? tp.getCode() : ctx.role().name();
        CanalConnexion canal = tp != null ? tp.getCanalConnexion() : canalParRole(ctx.role());

        String identifiantConnexion =
                canal == CanalConnexion.TELEPHONE || canal == CanalConnexion.LES_DEUX
                        ? (ctx.membre() != null && ctx.membre().getTelephone() != null
                                ? ctx.membre().getTelephone()
                                : u.getTelephone())
                        : u.getEmail();

        return ProfilResponse.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .prenom(u.getPrenom())
                .nom(u.getNom())
                .nomComplet(u.getPrenom() + " " + u.getNom())
                .role(ctx.role())
                .roleLabel(typeLibelle)
                .typeProfilId(tp != null ? tp.getId() : null)
                .typeProfilCode(typeCode)
                .typeProfilLibelle(typeLibelle)
                .canalConnexion(canal)
                .identifiantConnexion(identifiantConnexion)
                .organisationId(ctx.organisationId())
                .organisationNom(orgNom)
                .membreId(ctx.membreId())
                .codeMembre(ctx.membre() != null ? ctx.membre().getCodeMembre() : null)
                .posteMembre(ctx.membre() != null ? ctx.membre().getPoste() : null)
                .posteLabel(ctx.membre() != null ? libellePoste(ctx.membre().getPoste()) : null)
                .telephone(ctx.membre() != null && ctx.membre().getTelephone() != null
                        ? ctx.membre().getTelephone()
                        : u.getTelephone())
                .telephoneSecondaire(u.getTelephoneSecondaire())
                .adresse(u.getAdresse())
                .dateAdhesion(ctx.membre() != null ? ctx.membre().getDateAdhesion() : null)
                .dateCreation(u.getDateCreation())
                .actif(Boolean.TRUE.equals(u.getActif()))
                .superadminSansOrg(ctx.role() == Role.SUPERADMIN && ctx.organisationId() == null)
                .twoFactorEnabled(Boolean.TRUE.equals(u.getTotpEnabled()))
                .build();
    }

    private ProfilActiviteResponse toActivite(com.cotisapp.domain.entity.JournalAudit j) {
        return ProfilActiviteResponse.builder()
                .id(j.getId())
                .action(j.getAction())
                .details(j.getDetails())
                .libelle(libelleActivite(j.getAction(), j.getDetails()))
                .dateCreation(j.getDateCreation())
                .build();
    }

    private String libelleActivite(String action, String details) {
        if (details != null && !details.isBlank()) {
            return switch (action) {
                case "CONNEXION_REUSSIE" -> "Connexion réussie";
                case "PROFIL_MAJ" -> "Profil mis à jour";
                case "MOT_DE_PASSE_MAJ" -> "Mot de passe modifié";
                case "COTISATION", "COTISATION_MOIS" -> details;
                case "DEPENSE" -> "Dépense enregistrée · " + details;
                case "EMPRUNT" -> "Emprunt · " + details;
                default -> action.replace('_', ' ') + (details.isBlank() ? "" : " · " + details);
            };
        }
        return action.replace('_', ' ');
    }

    private void appliquerTelephoneUtilisateur(Utilisateur u, String telephone) {
        String tel = blankToNull(telephone);
        u.setTelephone(tel);
        u.setTelephoneNormalise(tel != null ? TelephoneUtil.normaliser(tel) : null);
    }

    private CanalConnexion canalParRole(Role role) {
        return switch (role) {
            case SUPERADMIN, ADMIN_GIE -> CanalConnexion.EMAIL;
            case MEMBRE -> CanalConnexion.TELEPHONE;
        };
    }

    private String libelleRole(Role role) {
        if (role == null) {
            return "Utilisateur";
        }
        return switch (role) {
            case SUPERADMIN -> "Superadmin";
            case ADMIN_GIE -> "Admin GIE";
            case MEMBRE -> "Membre";
        };
    }

    private String libellePoste(PosteMembre poste) {
        if (poste == null) {
            return null;
        }
        return switch (poste) {
            case SIMPLE -> "Membre simple";
            case PRESIDENT -> "Président(e)";
            case VICE_PRESIDENT -> "Vice-président(e)";
            case SECRETAIRE_GENERAL -> "Secrétaire général";
            case SECRETAIRE_GENERAL_ADJOINT -> "Secrétaire général adjoint";
            case TRESORIER -> "Trésorier(ère)";
            case SUPERVISEUR -> "Superviseur";
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ContexteProfil(
            Utilisateur utilisateur,
            Role role,
            Long organisationId,
            Long membreId,
            UtilisateurRole utilisateurRole,
            Membre membre,
            TypeProfil typeProfil) {}
}
