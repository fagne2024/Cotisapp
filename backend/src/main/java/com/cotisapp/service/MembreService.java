package com.cotisapp.service;

import com.cotisapp.domain.entity.CompteModeleMembre;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.dto.request.ComptesMembreSelection;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.dto.request.CreateMembreRequest;
import com.cotisapp.dto.request.UpdateMembreRequest;
import com.cotisapp.dto.response.MembreResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.util.TelephoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MembreService {

    private static final Pattern CODE_NUM = Pattern.compile("^(\\D+)-(\\d+)$");

    private final MembreRepository membreRepository;
    private final OrganisationRepository organisationRepository;
    private final CompteService compteService;
    private final CompteModeleMembreService compteModeleMembreService;
    private final ParametrageCompteService parametrageCompteService;
    private final MembreCompteAccesService membreCompteAccesService;
    private final MembreSuppressionService membreSuppressionService;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalService journalService;

    @Transactional
    public MembreResponse creer(Long organisationId, CreateMembreRequest request) {
        verifierAbsenceDoublon(organisationId, request);
        String code = genererCodeMembre(organisationId);
        Membre membre = Membre.builder()
                .organisationId(organisationId)
                .codeMembre(code)
                .prenom(request.getPrenom().trim())
                .nom(request.getNom().trim())
                .email(blankToNull(request.getEmail()))
                .telephone(blankToNull(request.getTelephone()))
                .poste(request.getPoste())
                .dateAdhesion(request.getDateAdhesion() != null ? request.getDateAdhesion() : LocalDate.now())
                .pieceIdentite(blankToNull(request.getPieceIdentite()))
                .actif(true)
                .paiementMobileActif(resoudrePaiementMobileActif(request.getPaiementMobileActif()))
                .build();
        appliquerTelephoneNormalise(membre);
        membre = membreRepository.save(membre);

        creerComptesSelectionnes(organisationId, membre.getId(), request.getComptes(), request.getModelesCompteIds());

        boolean creerCompte = !Boolean.FALSE.equals(request.getCreerCompteAcces());
        if (creerCompte) {
            String email = blankToNull(request.getEmail());
            if (email == null) {
                email = membre.getCodeMembre().toLowerCase().replace(" ", "") + "@membres.cotisapp.sn";
                membre.setEmail(email);
                membre = membreRepository.save(membre);
            }
            if (membre.getTelephoneNormalise() == null) {
                throw new BusinessException("Un numéro de téléphone valide est requis pour l'accès membre");
            }
            boolean envoyerEmail = !Boolean.FALSE.equals(request.getEnvoyerEmailActivation());
            membreCompteAccesService.creerCompteAccesPourMembre(
                    organisationId,
                    membre,
                    email,
                    request.getPoste(),
                    request.getTypeProfilId(),
                    true,
                    envoyerEmail);
            membre = membreRepository.findById(membre.getId()).orElse(membre);
        }

        String cible = JournalModificationFormatter.cibleMembre(
                membre.getCodeMembre(), membre.getPrenom(), membre.getNom(), membre.getId());
        journalService.enregistrer(
                organisationId,
                "MEMBRE_CREATION",
                JournalModificationFormatter.resumeCreation(
                        cible,
                        "poste " + JournalModificationFormatter.libellePoste(membre.getPoste()),
                        membre.getTelephone() != null ? "tél. " + membre.getTelephone() : null,
                        Boolean.FALSE.equals(request.getCreerCompteAcces())
                                ? "sans compte applicatif"
                                : "avec compte applicatif"));

        return toResponse(membre);
    }

    @Transactional
    public MembreResponse modifier(Long organisationId, Long membreId, UpdateMembreRequest request) {
        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, organisationId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));

        String prenomAvant = membre.getPrenom();
        String nomAvant = membre.getNom();
        String emailAvant = membre.getEmail();
        String telAvant = membre.getTelephone();
        PosteMembre posteAvant = membre.getPoste();
        LocalDate adhesionAvant = membre.getDateAdhesion();
        String pieceAvant = membre.getPieceIdentite();
        Boolean actifAvant = membre.getActif();
        Boolean paiementMobileAvant = membre.getPaiementMobileActif();

        membre.setPrenom(request.getPrenom().trim());
        membre.setNom(request.getNom().trim());
        membre.setEmail(blankToNull(request.getEmail()));
        membre.setTelephone(blankToNull(request.getTelephone()));
        membre.setPoste(request.getPoste());
        membre.setDateAdhesion(request.getDateAdhesion());
        membre.setPieceIdentite(blankToNull(request.getPieceIdentite()));
        membre.setActif(request.getActif());
        if (request.getPaiementMobileActif() != null && peutConfigurerPaiementMobile()) {
            membre.setPaiementMobileActif(request.getPaiementMobileActif());
        }
        appliquerTelephoneNormalise(membre);

        if (membre.getUtilisateurId() != null && membre.getTelephoneNormalise() == null) {
            throw new BusinessException("Un numéro de téléphone valide est requis pour un membre avec accès");
        }

        membre = membreRepository.save(membre);
        synchroniserUtilisateurLie(membre);

        List<String> changements = new ArrayList<>();
        JournalModificationFormatter.ajouterSiChange(changements, "Prénom", prenomAvant, membre.getPrenom());
        JournalModificationFormatter.ajouterSiChange(changements, "Nom", nomAvant, membre.getNom());
        JournalModificationFormatter.ajouterSiChange(changements, "E-mail", emailAvant, membre.getEmail());
        JournalModificationFormatter.ajouterSiChange(changements, "Téléphone", telAvant, membre.getTelephone());
        JournalModificationFormatter.ajouterSiChange(changements, "Poste", posteAvant, membre.getPoste());
        JournalModificationFormatter.ajouterSiChange(
                changements, "Date d'adhésion", adhesionAvant, membre.getDateAdhesion());
        JournalModificationFormatter.ajouterSiChange(changements, "Pièce d'identité", pieceAvant, membre.getPieceIdentite());
        JournalModificationFormatter.ajouterSiChange(changements, "Statut", actifAvant, membre.getActif());
        if (peutConfigurerPaiementMobile()) {
            JournalModificationFormatter.ajouterSiChange(
                    changements,
                    "Paiement mobile money (Mon compte)",
                    libellePaiementMobile(paiementMobileAvant),
                    libellePaiementMobile(membre.getPaiementMobileActif()));
        }
        String cible = JournalModificationFormatter.cibleMembre(
                membre.getCodeMembre(), membre.getPrenom(), membre.getNom(), membre.getId());
        journalService.enregistrer(
                organisationId, "MEMBRE_MAJ", JournalModificationFormatter.resumeModifications(cible, changements));

        return toResponse(membre);
    }

    private void synchroniserUtilisateurLie(Membre membre) {
        Long utilisateurId = membre.getUtilisateurId();
        if (utilisateurId == null) {
            return;
        }
        Utilisateur u = utilisateurRepository
                .findById(utilisateurId)
                .orElseThrow(() -> new BusinessException("Compte utilisateur lié introuvable"));

        if (membre.getEmail() != null && !membre.getEmail().isBlank()) {
            String emailNorm = membre.getEmail().trim().toLowerCase();
            if (!emailNorm.equalsIgnoreCase(u.getEmail())
                    && utilisateurRepository.existsByEmailAndIdNot(emailNorm, u.getId())) {
                throw new BusinessException("Cet email est déjà utilisé par un autre compte");
            }
            u.setEmail(emailNorm);
            membre.setEmail(emailNorm);
        }

        u.setPrenom(membre.getPrenom());
        u.setNom(membre.getNom());
        u.setTelephone(membre.getTelephone());
        u.setTelephoneNormalise(membre.getTelephoneNormalise());
        utilisateurRepository.save(u);
        membreRepository.save(membre);
    }

    @Transactional
    public void supprimer(Long organisationId, Long membreId) {
        membreSuppressionService.supprimer(organisationId, membreId);
    }

    @Transactional
    public com.cotisapp.dto.response.BulkPaiementMobileMembreResponse mettreAJourPaiementMobileEnMasse(
            Long organisationId, List<Long> membreIds, boolean actif) {
        if (!peutConfigurerPaiementMobile()) {
            throw new BusinessException(
                    "Seul l'administrateur GIE peut activer ou désactiver le paiement mobile money");
        }
        if (membreIds == null || membreIds.isEmpty()) {
            throw new BusinessException("Aucun membre sélectionné");
        }
        int count = 0;
        List<String> libelles = new ArrayList<>();
        for (Long membreId : membreIds) {
            Membre membre = membreRepository
                    .findByIdAndOrganisationId(membreId, organisationId)
                    .orElse(null);
            if (membre == null) {
                continue;
            }
            if (Boolean.TRUE.equals(membre.getPaiementMobileActif()) == actif) {
                continue;
            }
            membre.setPaiementMobileActif(actif);
            membreRepository.save(membre);
            count++;
            if (libelles.size() < 5) {
                libelles.add(membre.getCodeMembre());
            }
        }
        if (count > 0) {
            String liste = String.join(", ", libelles);
            if (count > libelles.size()) {
                liste += "… (+" + (count - libelles.size()) + ")";
            }
            journalService.enregistrer(
                    organisationId,
                    "MEMBRE_MAJ",
                    "Paiement mobile money (Mon compte) "
                            + libellePaiementMobile(actif)
                            + " pour "
                            + count
                            + " membre(s)"
                            + (liste.isBlank() ? "" : " : " + liste));
        }
        String action = actif ? "activé" : "désactivé";
        return com.cotisapp.dto.response.BulkPaiementMobileMembreResponse.builder()
                .nombreMisAJour(count)
                .actif(actif)
                .message(count == 0
                        ? "Aucune modification (déjà " + action + " pour la sélection)"
                        : "Mobile money " + action + " pour " + count + " membre(s)")
                .build();
    }

    private void creerComptesSelectionnes(
            Long organisationId,
            Long membreId,
            ComptesMembreSelection sel,
            List<Long> modelesIds) {
        if (sel.isEpargneHebdo()) {
            verifierFamilleActive(organisationId, FamilleCompte.EPARGNE_HEBDO);
            compteService.creerCompteMembre(organisationId, membreId, TypeCompte.EPARGNE_HEBDO, null);
        }
        if (sel.isEpargneMois()) {
            verifierFamilleActive(organisationId, FamilleCompte.EPARGNE_MOIS);
            compteService.creerCompteMembre(organisationId, membreId, TypeCompte.EPARGNE_MOIS, null);
        }
        if (sel.isSolidarite()) {
            verifierFamilleActive(organisationId, FamilleCompte.SOLIDARITE);
            compteService.creerCompteMembre(organisationId, membreId, TypeCompte.SOLIDARITE, null);
        }
        if (sel.isPenalite()) {
            verifierFamilleActive(organisationId, FamilleCompte.PENALITE);
            compteService.creerCompteMembre(organisationId, membreId, TypeCompte.PENALITE, null);
        }
        if (sel.isAmende()) {
            verifierFamilleActive(organisationId, FamilleCompte.AMENDE);
            compteService.creerCompteMembre(organisationId, membreId, TypeCompte.AMENDE, null);
        }

        if (modelesIds != null) {
            for (Long modeleId : modelesIds) {
                CompteModeleMembre modele = compteModeleMembreService.getEntity(organisationId, modeleId);
                if (!Boolean.TRUE.equals(modele.getActif())) {
                    throw new BusinessException("Le modèle de compte « " + modele.getLibelle() + " » est inactif");
                }
                compteService.creerCompteMembre(organisationId, membreId, TypeCompte.CUSTOM, modeleId);
            }
        }
    }

    private void verifierFamilleActive(Long organisationId, FamilleCompte famille) {
        if (!parametrageCompteService.familleActive(organisationId, famille)) {
            throw new BusinessException("Le type de compte « " + famille + " » n'est pas activé pour cette organisation");
        }
    }

    String genererCodeMembre(Long organisationId) {
        String prefix = organisationRepository.findById(organisationId)
                .map(o -> o.getCode().toUpperCase())
                .orElse("M");
        List<Membre> membres = membreRepository.findByOrganisationId(organisationId);
        int maxNum = 0;
        for (Membre m : membres) {
            Matcher mat = CODE_NUM.matcher(m.getCodeMembre());
            if (mat.matches() && mat.group(1).equalsIgnoreCase(prefix)) {
                maxNum = Math.max(maxNum, Integer.parseInt(mat.group(2)));
            }
        }
        return String.format("%s-%03d", prefix, maxNum + 1);
    }

    public MembreResponse toResponse(Membre m) {
        return MembreResponse.builder()
                .id(m.getId())
                .codeMembre(m.getCodeMembre())
                .nom(m.getNom())
                .prenom(m.getPrenom())
                .nomComplet(m.getNomComplet())
                .actif(m.getActif())
                .telephone(m.getTelephone())
                .email(m.getEmail())
                .poste(m.getPoste())
                .dateAdhesion(m.getDateAdhesion())
                .pieceIdentite(m.getPieceIdentite())
                .dateCreation(m.getDateCreation())
                .utilisateurId(m.getUtilisateurId())
                .compteAcces(m.getUtilisateurId() != null)
                .paiementMobileActif(Boolean.TRUE.equals(m.getPaiementMobileActif()))
                .build();
    }

    public List<MembreResponse> rechercher(Long organisationId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim();
        List<Membre> trouves = membreRepository.rechercherActifs(organisationId, q);
        if (trouves.isEmpty() && q.contains(" ")) {
            trouves = membreRepository.rechercherActifs(organisationId, q.replace(" ", ""));
        }
        return trouves.stream()
                .limit(25)
                .map(this::toResponse)
                .toList();
    }

    private static boolean peutConfigurerPaiementMobile() {
        Role role = OrganisationContext.getRole();
        return role == Role.ADMIN_GIE || role == Role.SUPERADMIN;
    }

    private static boolean resoudrePaiementMobileActif(Boolean demande) {
        if (!peutConfigurerPaiementMobile()) {
            return false;
        }
        return Boolean.TRUE.equals(demande);
    }

    private static String libellePaiementMobile(Boolean actif) {
        return Boolean.TRUE.equals(actif) ? "activé" : "désactivé";
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    /**
     * Refuse les doublons dans le GIE (e-mail, téléphone normalisé, pièce d'identité).
     * Utilisé à la création manuelle et à l'import.
     */
    void verifierAbsenceDoublon(Long organisationId, CreateMembreRequest request) {
        String email = blankToNull(request.getEmail());
        if (email != null
                && membreRepository.existsByOrganisationIdAndEmailIgnoreCase(organisationId, email)) {
            throw new BusinessException("Un membre avec l'e-mail « " + email + " » existe déjà dans ce GIE");
        }
        String telephone = blankToNull(request.getTelephone());
        if (telephone != null) {
            String normalise = TelephoneUtil.normaliser(telephone);
            if (normalise != null
                    && membreRepository.existsByOrganisationIdAndTelephoneNormalise(organisationId, normalise)) {
                throw new BusinessException(
                        "Un membre avec le numéro « " + telephone + " » existe déjà dans ce GIE");
            }
        }
        String piece = blankToNull(request.getPieceIdentite());
        if (piece != null
                && membreRepository.existsByOrganisationIdAndPieceIdentiteIgnoreCase(organisationId, piece)) {
            throw new BusinessException(
                    "Un membre avec la pièce d'identité « " + piece + " » existe déjà dans ce GIE");
        }
    }

    /** Met à jour {@code telephoneNormalise} à partir de {@code telephone} (connexion membre). */
    public static void appliquerTelephoneNormalise(Membre membre) {
        if (membre.getTelephone() == null || membre.getTelephone().isBlank()) {
            membre.setTelephone(null);
            membre.setTelephoneNormalise(null);
            return;
        }
        String tel = membre.getTelephone().trim();
        membre.setTelephone(tel);
        membre.setTelephoneNormalise(TelephoneUtil.normaliser(tel));
    }
}
