package com.cotisapp.service;

import com.cotisapp.domain.entity.JournalAudit;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.Utilisateur;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeEvenementJournal;
import com.cotisapp.repository.JournalAuditRepository;
import com.cotisapp.repository.UtilisateurRepository;
import com.cotisapp.security.OrganisationContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalAuditRepository journalAuditRepository;
    private final UtilisateurRepository utilisateurRepository;

    /** Journal d'une opération caisse avec libellé métier explicite (membre, montant, type). */
    @Transactional
    public void enregistrerOperation(Long organisationId, String action, Operation operation, Membre membre) {
        if (operation == null) {
            enregistrer(organisationId, action, action);
            return;
        }
        String libelle = detailsOperation(operation, membre);
        enregistrer(organisationId, action, libelle);
    }

    static String detailsOperation(Operation operation, Membre membre) {
        Map<Long, Membre> membres =
                membre != null && membre.getId() != null ? Map.of(membre.getId(), membre) : Map.of();
        var ctx = new JournalCaisseLibelleFormatter.Context(membres, Map.of());
        String libelle = JournalCaisseLibelleFormatter.format(operation, ctx);
        if (libelle == null || libelle.isBlank()) {
            libelle = operation.getTypeOperation() != null
                    ? operation.getTypeOperation().name()
                    : "Opération";
        }
        return libelle + " (réf. opération n°" + operation.getId() + ")";
    }

    /** Compatibilité : opérations métier historiques. */
    @Transactional
    public void enregistrer(Long organisationId, String action, String details) {
        enregistrer(JournalAudit.builder()
                .organisationId(organisationId)
                .utilisateurId(OrganisationContext.getUserId())
                .action(action)
                .typeEvenement(TypeEvenementJournal.ACTION_METIER)
                .details(JournalAuditLibelleFormatter.enrichirDetailsAction(action, details))
                .succes(true));
    }

    @Transactional
    public void enregistrer(JournalAudit.JournalAuditBuilder builder) {
        JournalAudit entree = builder.build();
        enrichirUtilisateur(entree);
        if (entree.getTypeEvenement() == null) {
            entree.setTypeEvenement(TypeEvenementJournal.ACTION_METIER);
        }
        if (entree.getSucces() == null) {
            entree.setSucces(true);
        }
        log.info(
                "Journal org={} user={} type={} action={}",
                entree.getOrganisationId(),
                entree.getUtilisateurId(),
                entree.getTypeEvenement(),
                entree.getAction());
        journalAuditRepository.save(entree);
    }

    @Transactional
    public void enregistrerConnexionReussie(
            Long organisationId,
            Long utilisateurId,
            Role role,
            Long membreId,
            String details,
            HttpServletRequest request) {
        Utilisateur u = utilisateurRepository.findById(utilisateurId).orElse(null);
        enregistrer(JournalAudit.builder()
                .organisationId(organisationId)
                .utilisateurId(utilisateurId)
                .utilisateurEmail(u != null ? u.getEmail() : null)
                .utilisateurNom(u != null ? formatNom(u) : null)
                .role(role)
                .membreId(membreId)
                .action("CONNEXION_REUSSIE")
                .typeEvenement(TypeEvenementJournal.CONNEXION)
                .details(details)
                .ipAddress(extraireIp(request))
                .userAgent(tronquer(request != null ? request.getHeader("User-Agent") : null, 500))
                .succes(true));
    }

    @Transactional
    public void enregistrerConnexionEchec(
            Long organisationId,
            String identifiant,
            String motif,
            HttpServletRequest request) {
        enregistrer(JournalAudit.builder()
                .organisationId(organisationId)
                .utilisateurEmail(identifiant != null ? identifiant.trim().toLowerCase() : null)
                .action("CONNEXION_ECHEC")
                .typeEvenement(TypeEvenementJournal.CONNEXION_ECHEC)
                .details(motif)
                .ipAddress(extraireIp(request))
                .userAgent(tronquer(request != null ? request.getHeader("User-Agent") : null, 500))
                .succes(false));
    }

    @Transactional
    public void enregistrerDeconnexion(Long organisationId, Long utilisateurId, HttpServletRequest request) {
        Utilisateur u = utilisateurRepository.findById(utilisateurId).orElse(null);
        enregistrer(JournalAudit.builder()
                .organisationId(organisationId)
                .utilisateurId(utilisateurId)
                .utilisateurEmail(u != null ? u.getEmail() : null)
                .utilisateurNom(u != null ? formatNom(u) : null)
                .role(OrganisationContext.getRole())
                .membreId(OrganisationContext.getMembreId())
                .action("DECONNEXION")
                .typeEvenement(TypeEvenementJournal.DECONNEXION)
                .details("Session fermée — déconnexion volontaire de l'application")
                .ipAddress(extraireIp(request))
                .userAgent(tronquer(request != null ? request.getHeader("User-Agent") : null, 500))
                .succes(true));
    }

    @Transactional
    public void enregistrerVisiteModule(
            Long organisationId,
            String moduleCode,
            String moduleLibelle,
            String routePath,
            String details) {
        enregistrerVisiteModule(organisationId, moduleCode, moduleLibelle, routePath, details, null);
    }

    @Transactional
    public void enregistrerVisiteModule(
            Long organisationId,
            String moduleCode,
            String moduleLibelle,
            String routePath,
            String details,
            HttpServletRequest request) {
        String detailsFin =
                details != null && !details.isBlank() ? details : "Consultation de « " + moduleLibelle + " »";
        enregistrer(JournalAudit.builder()
                .organisationId(organisationId)
                .utilisateurId(OrganisationContext.getUserId())
                .role(OrganisationContext.getRole())
                .membreId(OrganisationContext.getMembreId())
                .action("MODULE_VISITE")
                .typeEvenement(TypeEvenementJournal.MODULE_VISITE)
                .moduleCode(moduleCode)
                .moduleLibelle(moduleLibelle)
                .routePath(tronquer(routePath, 500))
                .details(detailsFin)
                .ipAddress(extraireIp(request))
                .userAgent(tronquer(request != null ? request.getHeader("User-Agent") : null, 500))
                .succes(true));
    }

    private void enrichirUtilisateur(JournalAudit entree) {
        if (entree.getUtilisateurId() == null) {
            entree.setUtilisateurId(OrganisationContext.getUserId());
        }
        if (entree.getRole() == null) {
            entree.setRole(OrganisationContext.getRole());
        }
        if (entree.getMembreId() == null) {
            entree.setMembreId(OrganisationContext.getMembreId());
        }
        if (entree.getUtilisateurId() != null
                && (entree.getUtilisateurEmail() == null || entree.getUtilisateurNom() == null)) {
            utilisateurRepository.findById(entree.getUtilisateurId()).ifPresent(u -> {
                if (entree.getUtilisateurEmail() == null) {
                    entree.setUtilisateurEmail(u.getEmail());
                }
                if (entree.getUtilisateurNom() == null) {
                    entree.setUtilisateurNom(formatNom(u));
                }
            });
        }
    }

    static String formatNom(Utilisateur u) {
        return ((u.getPrenom() != null ? u.getPrenom().trim() : "")
                        + " "
                        + (u.getNom() != null ? u.getNom().trim() : ""))
                .trim();
    }

    static String extraireIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static String tronquer(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
