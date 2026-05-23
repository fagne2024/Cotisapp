package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.entity.UtilisateurRole;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.request.AdminGieCreationRequest;
import com.cotisapp.dto.request.AdminGieUpsertRequest;
import com.cotisapp.dto.request.CreateUtilisateurOrgRequest;
import com.cotisapp.dto.request.ReinitialiserAdminMdpRequest;
import com.cotisapp.dto.response.UtilisateurAccesStatsResponse;
import com.cotisapp.dto.response.UtilisateurOrgResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.JournalAuditRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.TypeProfilRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.repository.UtilisateurRoleRepository;
import com.cotisapp.util.TelephoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UtilisateurAccesService {

    private static final DateTimeFormatter DATE_HEURE =
            DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm", Locale.FRENCH);

    @Value("${cotisapp.init.mdp-defaut:Admin@2026}")
    private String mdpDefaut;

    private final UtilisateurRoleRepository utilisateurRoleRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MembreRepository membreRepository;
    private final PasswordEncoder passwordEncoder;
    private final TypeProfilRepository typeProfilRepository;
    private final ActivationEmailService activationEmailService;
    private final MembreCompteAccesService membreCompteAccesService;
    private final PresenceService presenceService;
    private final JournalAuditRepository journalAuditRepository;
    private final JournalService journalService;

    @Transactional(readOnly = true)
    public UtilisateurAccesStatsResponse statistiques(Long organisationId) {
        List<UtilisateurOrgResponse> users = lister(organisationId, null, null);
        long actifs = users.stream().filter(u -> Boolean.TRUE.equals(u.getActif())).count();
        long connectes = users.stream().filter(UtilisateurOrgResponse::isEnLigne).count();
        return UtilisateurAccesStatsResponse.builder()
                .total(users.size())
                .actifs(actifs)
                .suspendus(users.size() - actifs)
                .connectesMaintenant(connectes)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UtilisateurOrgResponse> lister(Long organisationId, Role roleFiltre, Boolean actifFiltre) {
        Map<Long, ConnexionUtilisateurStats> connexionParUtilisateur =
                chargerStatsConnexion(organisationId);
        return utilisateurRoleRepository.findByOrganisationId(organisationId).stream()
                .filter(ur -> ur.getRole() == Role.ADMIN_GIE || ur.getRole() == Role.MEMBRE)
                .map(ur -> toResponse(ur, organisationId, connexionParUtilisateur))
                .filter(Objects::nonNull)
                .filter(u -> roleFiltre == null || u.getRole() == roleFiltre)
                .filter(u -> actifFiltre == null || Objects.equals(u.getActif(), actifFiltre))
                .sorted(Comparator
                        .comparing((UtilisateurOrgResponse u) -> u.getRole() == Role.ADMIN_GIE ? 0 : 1)
                        .thenComparing(UtilisateurOrgResponse::getNom))
                .toList();
    }

    @Transactional
    public UtilisateurOrgResponse creer(Long organisationId, CreateUtilisateurOrgRequest request) {
        if (request.getRole() != Role.ADMIN_GIE && request.getRole() != Role.MEMBRE) {
            throw new BusinessException("Rôle non autorisé pour une organisation");
        }

        if (request.getRole() == Role.MEMBRE) {
            PosteMembre poste = request.getPoste() != null ? request.getPoste() : PosteMembre.SIMPLE;
            Long membreId = request.getMembreId();
            if (membreId == null && estPosteBureau(poste)) {
                return creerCompteBureauSansMembre(organisationId, request, poste);
            }
            if (membreId == null) {
                throw new BusinessException(
                        "Sélectionnez un membre sans compte, ou créez le membre depuis le module Membres");
            }
            Membre membre = membreRepository.findByIdAndOrganisationId(membreId, organisationId)
                    .orElseThrow(() -> new BusinessException("Membre introuvable"));
            if (membre.getUtilisateurId() != null) {
                throw new BusinessException("Ce membre a déjà un compte utilisateur");
            }
            membre.setPrenom(request.getPrenom().trim());
            membre.setNom(request.getNom().trim());
            PosteMembre posteMembre = request.getPoste() != null ? request.getPoste() : membre.getPoste();
            membreCompteAccesService.creerCompteAccesPourMembre(
                    organisationId,
                    membre,
                    request.getEmail(),
                    posteMembre,
                    request.getTypeProfilId(),
                    Boolean.TRUE.equals(request.getCompteActif()),
                    !Boolean.FALSE.equals(request.getEnvoyerEmailActivation()));
            UtilisateurRole ur = utilisateurRoleRepository
                    .findFirstByUtilisateurIdAndRoleAndOrganisationIdOrderByIdAsc(
                            membre.getUtilisateurId(), Role.MEMBRE, organisationId)
                    .orElseThrow(() -> new BusinessException("Compte membre créé mais rôle introuvable"));
            Utilisateur u = utilisateurRepository.findById(membre.getUtilisateurId()).orElse(null);
            if (u != null) {
                journaliserCreationCompte(organisationId, u, Role.MEMBRE, membre.getCodeMembre(), membre.getPoste());
            }
            return toResponse(ur, organisationId);
        }

        if (utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Un utilisateur avec cet email existe déjà");
        }
        String pwd = request.getMotDePasse();
        boolean pwdGenere = pwd == null || pwd.isBlank();
        if (pwdGenere) {
            pwd = mdpDefaut;
        }
        Utilisateur user = utilisateurRepository.save(Utilisateur.builder()
                .email(request.getEmail().trim().toLowerCase())
                .motDePasse(passwordEncoder.encode(pwd))
                .prenom(request.getPrenom().trim())
                .nom(request.getNom().trim())
                .actif(Boolean.TRUE.equals(request.getCompteActif()))
                .doitChangerMotDePasse(pwdGenere)
                .build());

        if (utilisateurRoleRepository.findFirstByOrganisationIdAndRole(organisationId, Role.ADMIN_GIE).isPresent()) {
            throw new BusinessException("Un administrateur GIE existe déjà pour cette organisation");
        }

        Long typeProfilId = request.getTypeProfilId();
        if (typeProfilId != null) {
            var tp = typeProfilRepository
                    .findById(typeProfilId)
                    .filter(t -> t.getOrganisationId() == null
                            || Objects.equals(t.getOrganisationId(), organisationId))
                    .orElseThrow(() -> new BusinessException("Type de profil invalide pour cette organisation"));
            if (tp.getRole() != Role.ADMIN_GIE) {
                throw new BusinessException(
                        "Un administrateur GIE doit utiliser le profil « Admin GIE » (tous les droits sur l'organisation)");
            }
        } else {
            typeProfilId = typeProfilRepository.findFirstByOrganisationIdIsNullAndCodeOrderByIdAsc("ADMIN_GIE")
                    .map(com.cotisapp.domain.entity.TypeProfil::getId)
                    .orElse(null);
        }

        UtilisateurRole ur = utilisateurRoleRepository.save(UtilisateurRole.builder()
                .utilisateurId(user.getId())
                .role(Role.ADMIN_GIE)
                .organisationId(organisationId)
                .typeProfilId(typeProfilId)
                .build());

        journaliserCreationCompte(organisationId, user, Role.ADMIN_GIE, null, null);
        return toResponse(ur, organisationId);
    }

    @Transactional
    public UtilisateurOrgResponse upsertAdminGie(Long organisationId, AdminGieUpsertRequest request) {
        var existingRole = utilisateurRoleRepository.findFirstByOrganisationIdAndRole(organisationId, Role.ADMIN_GIE);
        if (existingRole.isEmpty()) {
            CreateUtilisateurOrgRequest create = new CreateUtilisateurOrgRequest();
            create.setPrenom(request.getPrenom());
            create.setNom(request.getNom());
            create.setEmail(request.getEmail());
            create.setMotDePasse(request.getMotDePasse());
            create.setRole(Role.ADMIN_GIE);
            create.setCompteActif(Boolean.TRUE.equals(request.getCompteActif()));
            return creer(organisationId, create);
        }

        Long userId = existingRole.get().getUtilisateurId();
        Utilisateur user = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Administrateur GIE introuvable"));

        String prenomAvant = user.getPrenom();
        String nomAvant = user.getNom();
        String emailAvant = user.getEmail();
        Boolean actifAvant = user.getActif();
        boolean mdpChange = request.getMotDePasse() != null && !request.getMotDePasse().isBlank();

        String email = request.getEmail().trim().toLowerCase();
        utilisateurRepository.findByEmail(email).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new BusinessException("Un utilisateur avec cet email existe déjà");
            }
        });

        user.setPrenom(request.getPrenom().trim());
        user.setNom(request.getNom().trim());
        user.setEmail(email);
        if (request.getCompteActif() != null) {
            user.setActif(request.getCompteActif());
        }
        if (request.getMotDePasse() != null && !request.getMotDePasse().isBlank()) {
            user.setMotDePasse(passwordEncoder.encode(request.getMotDePasse()));
            user.setDoitChangerMotDePasse(Boolean.TRUE.equals(request.getForcerChangementMotDePasse()));
        }
        utilisateurRepository.save(user);

        List<String> changements = new ArrayList<>();
        JournalModificationFormatter.ajouterSiChange(changements, "Prénom", prenomAvant, user.getPrenom());
        JournalModificationFormatter.ajouterSiChange(changements, "Nom", nomAvant, user.getNom());
        JournalModificationFormatter.ajouterSiChange(changements, "E-mail", emailAvant, user.getEmail());
        JournalModificationFormatter.ajouterSiChange(changements, "Statut compte", actifAvant, user.getActif());
        if (mdpChange) {
            changements.add("Mot de passe : réinitialisé"
                    + (Boolean.TRUE.equals(request.getForcerChangementMotDePasse())
                            ? " (changement obligatoire à la prochaine connexion)"
                            : ""));
        }
        String cible = JournalModificationFormatter.cibleUtilisateur(
                user.getPrenom(), user.getNom(), user.getEmail(), user.getId());
        journalService.enregistrer(
                organisationId,
                "UTILISATEUR_MAJ",
                JournalModificationFormatter.resumeModifications("Admin GIE " + cible, changements));

        return toResponse(existingRole.get(), organisationId);
    }

    @Transactional
    public UtilisateurOrgResponse reinitialiserMotDePasseAdminGie(
            Long organisationId, ReinitialiserAdminMdpRequest request) {
        UtilisateurRole adminRole = utilisateurRoleRepository
                .findFirstByOrganisationIdAndRole(organisationId, Role.ADMIN_GIE)
                .orElseThrow(() -> new BusinessException("Aucun administrateur GIE pour cette organisation"));

        Utilisateur user = utilisateurRepository
                .findById(adminRole.getUtilisateurId())
                .orElseThrow(() -> new BusinessException("Administrateur GIE introuvable"));

        String pwd = request.getMotDePasse();
        if (pwd == null || pwd.isBlank()) {
            pwd = mdpDefaut;
        }
        user.setMotDePasse(passwordEncoder.encode(pwd));
        user.setDoitChangerMotDePasse(Boolean.TRUE.equals(request.getForcerChangement()));
        utilisateurRepository.save(user);

        String cible = JournalModificationFormatter.cibleUtilisateur(
                user.getPrenom(), user.getNom(), user.getEmail(), user.getId());
        journalService.enregistrer(
                organisationId,
                "MOT_DE_PASSE_MAJ",
                "Mot de passe réinitialisé pour l'admin GIE "
                        + cible
                        + (Boolean.TRUE.equals(request.getForcerChangement())
                                ? " — changement obligatoire à la prochaine connexion"
                                : ""));

        return toResponse(adminRole, organisationId);
    }

    @Transactional
    public UtilisateurOrgResponse reinitialiserTwoFactorAdminGie(Long organisationId) {
        UtilisateurRole adminRole = utilisateurRoleRepository
                .findFirstByOrganisationIdAndRole(organisationId, Role.ADMIN_GIE)
                .orElseThrow(() -> new BusinessException("Aucun administrateur GIE pour cette organisation"));

        Utilisateur user = utilisateurRepository
                .findById(adminRole.getUtilisateurId())
                .orElseThrow(() -> new BusinessException("Administrateur GIE introuvable"));

        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        utilisateurRepository.save(user);

        String cible = JournalModificationFormatter.cibleUtilisateur(
                user.getPrenom(), user.getNom(), user.getEmail(), user.getId());
        journalService.enregistrer(
                organisationId,
                "2FA_DESACTIVE",
                "Double authentification désactivée pour l'admin GIE " + cible);

        return toResponse(adminRole, organisationId);
    }

    @Transactional
    public UtilisateurOrgResponse upsertAdminGie(Long organisationId, AdminGieCreationRequest request) {
        AdminGieUpsertRequest upsert = new AdminGieUpsertRequest();
        upsert.setPrenom(request.getPrenom());
        upsert.setNom(request.getNom());
        upsert.setEmail(request.getEmail());
        upsert.setMotDePasse(request.getMotDePasse());
        upsert.setCompteActif(true);
        return upsertAdminGie(organisationId, upsert);
    }

    @Transactional
    public UtilisateurOrgResponse basculerActif(Long organisationId, Long utilisateurId, boolean actif) {
        Utilisateur user = chargerUtilisateurOrg(organisationId, utilisateurId);
        Boolean actifAvant = user.getActif();
        user.setActif(actif);
        utilisateurRepository.save(user);
        UtilisateurRole ur = utilisateurRoleRepository.findByUtilisateurId(user.getId()).stream()
                .filter(r -> Objects.equals(r.getOrganisationId(), organisationId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Rôle introuvable"));
        String cible = JournalModificationFormatter.cibleUtilisateur(
                user.getPrenom(), user.getNom(), user.getEmail(), user.getId());
        List<String> changements = new ArrayList<>();
        JournalModificationFormatter.ajouterSiChange(
                changements,
                "Statut compte",
                JournalModificationFormatter.libelleActif(actifAvant),
                JournalModificationFormatter.libelleActif(actif));
        journalService.enregistrer(
                organisationId,
                "UTILISATEUR_MAJ",
                JournalModificationFormatter.resumeModifications(
                        JournalModificationFormatter.libelleRole(ur.getRole()) + " " + cible, changements));
        return toResponse(ur, organisationId);
    }

    private UtilisateurOrgResponse creerCompteBureauSansMembre(
            Long organisationId, CreateUtilisateurOrgRequest request, PosteMembre poste) {
        if (!estPosteBureau(poste)) {
            throw new BusinessException("Poste bureau invalide");
        }
        String emailNorm = request.getEmail().trim().toLowerCase();
        if (utilisateurRepository.existsByEmail(emailNorm)) {
            throw new BusinessException("Un utilisateur avec cet email existe déjà");
        }
        Long typeProfilId = resoudreTypeProfilBureau(organisationId, request.getTypeProfilId(), poste);

        Utilisateur user = utilisateurRepository.save(Utilisateur.builder()
                .email(emailNorm)
                .motDePasse(passwordEncoder.encode(ActivationEmailService.MDP_MEMBRE_INITIAL))
                .prenom(request.getPrenom().trim())
                .nom(request.getNom().trim())
                .actif(Boolean.TRUE.equals(request.getCompteActif()))
                .doitChangerMotDePasse(true)
                .build());

        UtilisateurRole ur = utilisateurRoleRepository.save(UtilisateurRole.builder()
                .utilisateurId(user.getId())
                .role(Role.MEMBRE)
                .organisationId(organisationId)
                .membreId(null)
                .typeProfilId(typeProfilId)
                .build());

        if (!Boolean.FALSE.equals(request.getEnvoyerEmailActivation())) {
            activationEmailService.envoyerMotDePasseMembre(
                    user.getEmail(), user.getPrenom(), ActivationEmailService.MDP_MEMBRE_INITIAL);
        }
        journaliserCreationCompte(organisationId, user, Role.MEMBRE, null, poste);
        return toResponse(ur, organisationId);
    }

    private void journaliserCreationCompte(
            Long organisationId,
            Utilisateur user,
            Role role,
            String codeMembre,
            PosteMembre poste) {
        String cible = JournalModificationFormatter.cibleUtilisateur(
                user.getPrenom(), user.getNom(), user.getEmail(), user.getId());
        List<String> attrs = new ArrayList<>();
        attrs.add("rôle " + JournalModificationFormatter.libelleRole(role));
        if (codeMembre != null) {
            attrs.add("lié au membre " + codeMembre);
        }
        if (poste != null) {
            attrs.add("poste " + JournalModificationFormatter.libellePoste(poste));
        }
        attrs.add("statut " + JournalModificationFormatter.libelleActif(user.getActif()));
        journalService.enregistrer(
                organisationId,
                "UTILISATEUR_CREATION",
                JournalModificationFormatter.resumeCreation(cible, attrs.toArray(String[]::new)));
    }

    private Long resoudreTypeProfilBureau(Long organisationId, Long typeProfilId, PosteMembre poste) {
        if (typeProfilId != null) {
            var tp = typeProfilRepository
                    .findById(typeProfilId)
                    .filter(t -> t.getOrganisationId() == null
                            || Objects.equals(t.getOrganisationId(), organisationId))
                    .orElseThrow(() -> new BusinessException("Type de profil invalide pour cette organisation"));
            if (tp.getRole() != Role.MEMBRE) {
                throw new BusinessException("Le profil applicatif doit être un profil membre de bureau");
            }
            if (tp.getPosteMembre() != null
                    && tp.getPosteMembre() != PosteMembre.SIMPLE
                    && tp.getPosteMembre() != poste) {
                throw new BusinessException(
                        "Le profil « " + tp.getLibelle() + " » ne correspond pas au poste sélectionné");
            }
            return typeProfilId;
        }
        return typeProfilRepository
                .findFirstByOrganisationIdAndRoleAndPosteMembre(organisationId, Role.MEMBRE, poste)
                .map(com.cotisapp.domain.entity.TypeProfil::getId)
                .orElseThrow(() -> new BusinessException(
                        "Profil applicatif introuvable pour ce poste. Rechargez la page Utilisateurs."));
    }

    private static boolean estPosteBureau(PosteMembre poste) {
        return poste != null && poste != PosteMembre.SIMPLE;
    }

    private Utilisateur chargerUtilisateurOrg(Long organisationId, Long utilisateurId) {
        boolean belongs = utilisateurRoleRepository.findByUtilisateurId(utilisateurId).stream()
                .anyMatch(r -> Objects.equals(r.getOrganisationId(), organisationId));
        if (!belongs) {
            throw new BusinessException("Utilisateur hors organisation");
        }
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new BusinessException("Utilisateur introuvable"));
    }

    private Map<Long, ConnexionUtilisateurStats> chargerStatsConnexion(Long organisationId) {
        Map<Long, ConnexionUtilisateurStats> map = new HashMap<>();
        for (Object[] row : journalAuditRepository.findDerniereConnexionParUtilisateur(organisationId)) {
            Long userId = (Long) row[0];
            LocalDateTime derniere = (LocalDateTime) row[1];
            map.computeIfAbsent(userId, id -> new ConnexionUtilisateurStats()).derniereConnexion = derniere;
        }
        LocalDateTime depuis30j = LocalDateTime.now().minusDays(30);
        for (Object[] row : journalAuditRepository.countConnexions30jParUtilisateur(organisationId, depuis30j)) {
            Long userId = (Long) row[0];
            long count = (Long) row[1];
            map.computeIfAbsent(userId, id -> new ConnexionUtilisateurStats()).connexions30j = (int) count;
        }
        return map;
    }

    private UtilisateurOrgResponse toResponse(UtilisateurRole ur, Long organisationId) {
        return toResponse(ur, organisationId, chargerStatsConnexion(organisationId));
    }

    private UtilisateurOrgResponse toResponse(
            UtilisateurRole ur, Long organisationId, Map<Long, ConnexionUtilisateurStats> connexionParUtilisateur) {
        if (!Objects.equals(ur.getOrganisationId(), organisationId)) {
            return null;
        }
        Utilisateur u = utilisateurRepository.findById(ur.getUtilisateurId()).orElse(null);
        if (u == null) {
            return null;
        }
        Membre membre = null;
        if (ur.getMembreId() != null) {
            membre = membreRepository.findById(ur.getMembreId()).orElse(null);
        } else if (ur.getRole() == Role.MEMBRE) {
            membre = membreRepository.findFirstByUtilisateurIdAndOrganisationIdOrderByIdAsc(u.getId(), organisationId).orElse(null);
        }

        PosteMembre poste = membre != null ? membre.getPoste() : null;
        String codeMembre = membre != null ? membre.getCodeMembre() : null;
        Long membreId = membre != null ? membre.getId() : ur.getMembreId();

        String typeProfilCode = null;
        String typeProfilLibelle = null;
        if (ur.getTypeProfilId() != null) {
            var tp = typeProfilRepository.findById(ur.getTypeProfilId()).orElse(null);
            if (tp != null) {
                typeProfilCode = tp.getCode();
                typeProfilLibelle = tp.getLibelle();
                if (poste == null && tp.getPosteMembre() != null) {
                    poste = tp.getPosteMembre();
                }
            }
        }

        ConnexionUtilisateurStats statsConnexion =
                connexionParUtilisateur.getOrDefault(u.getId(), new ConnexionUtilisateurStats());
        boolean enLigne = Boolean.TRUE.equals(u.getActif())
                && presenceService.isOnline(u.getId(), organisationId);
        Optional<LocalDateTime> derniereActivite =
                presenceService.derniereActivite(u.getId(), organisationId);
        LocalDateTime refConnexion = statsConnexion.derniereConnexion;
        if (derniereActivite.isPresent()) {
            LocalDateTime act = derniereActivite.get();
            if (refConnexion == null || act.isAfter(refConnexion)) {
                refConnexion = act;
            }
        }
        Optional<LocalDateTime> derniereConnexion = Optional.ofNullable(refConnexion);

        return UtilisateurOrgResponse.builder()
                .utilisateurId(u.getId())
                .roleId(ur.getId())
                .membreId(membreId)
                .email(u.getEmail())
                .telephone(u.getTelephone())
                .nom(u.getNom())
                .prenom(u.getPrenom())
                .nomComplet(u.getPrenom() + " " + u.getNom())
                .role(ur.getRole())
                .poste(poste)
                .typeProfilCode(typeProfilCode)
                .typeProfilLibelle(typeProfilLibelle)
                .codeMembre(codeMembre)
                .actif(u.getActif())
                .derniereConnexionLibelle(libelleDerniereConnexion(enLigne, derniereConnexion))
                .connexions30j(statsConnexion.connexions30j)
                .enLigne(enLigne)
                .build();
    }

    private static String libelleDerniereConnexion(boolean enLigne, Optional<LocalDateTime> derniereConnexion) {
        if (derniereConnexion.isEmpty()) {
            return "Jamais";
        }
        LocalDateTime ref = derniereConnexion.get();
        long days = ChronoUnit.DAYS.between(ref.toLocalDate(), LocalDateTime.now().toLocalDate());
        if (enLigne || days == 0) {
            return "Aujourd'hui " + DateTimeFormatter.ofPattern("HH:mm").format(ref);
        }
        if (days == 1) {
            return "Hier " + DateTimeFormatter.ofPattern("HH:mm").format(ref);
        }
        return DATE_HEURE.format(ref);
    }

    private static final class ConnexionUtilisateurStats {
        private LocalDateTime derniereConnexion;
        private int connexions30j;
    }
}
