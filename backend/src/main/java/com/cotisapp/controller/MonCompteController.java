package com.cotisapp.controller;

import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.dto.request.RembourserRequest;
import com.cotisapp.dto.response.DemandeOperationMembreResponse;
import com.cotisapp.dto.response.EmpruntResponse;
import com.cotisapp.dto.response.JourneeReunionResponse;
import com.cotisapp.dto.response.MonCompteFicheResponse;
import com.cotisapp.dto.response.MouvementPreviewResponse;
import com.cotisapp.dto.response.RecapMembreJourneeResponse;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.service.DemandeOperationMembreService;
import com.cotisapp.service.EmpruntService;
import com.cotisapp.service.MembreFicheService;
import com.cotisapp.service.OperationMapperService;
import com.cotisapp.service.RecapJourneeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/mon-compte")
@RequiredArgsConstructor
public class MonCompteController {

    private static final DateTimeFormatter MOIS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MembreFicheService membreFicheService;
    private final RecapJourneeService recapJourneeService;
    private final EmpruntService empruntService;
    private final OperationMapperService operationMapperService;
    private final DemandeOperationMembreService demandeOperationMembreService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public MonCompteFicheResponse fiche(
            @PathVariable Long orgId,
            @RequestParam(required = false) String mois) {
        Long membreId = OrganisationContext.getMembreId();
        if (membreId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Compte membre non associé à cet utilisateur");
        }
        String moisAnnee = mois != null && !mois.isBlank() ? mois : YearMonth.now().format(MOIS_FORMAT);
        return membreFicheService.chargerFicheMonCompte(orgId, membreId, moisAnnee);
    }

    @GetMapping("/emprunts")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<EmpruntResponse> listerMesEmprunts(
            @PathVariable Long orgId,
            @RequestParam(required = false) TypeEmprunt type) {
        Long membreId = requireMembreId();
        List<EmpruntResponse> emprunts = empruntService.listerParMembre(orgId, membreId);
        if (type == null) {
            return emprunts;
        }
        return emprunts.stream().filter(e -> e.getTypeEmprunt() == type).toList();
    }

    @GetMapping("/emprunts/suivi")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<EmpruntResponse> listerMesEmpruntsSuivi(
            @PathVariable Long orgId,
            @RequestParam(required = false) TypeEmprunt type) {
        Long membreId = requireMembreId();
        List<EmpruntResponse> emprunts = empruntService.listerPourSuiviParMembre(orgId, membreId);
        if (type == null) {
            return emprunts;
        }
        return emprunts.stream().filter(e -> e.getTypeEmprunt() == type).toList();
    }

    @GetMapping("/recap-journees")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<JourneeReunionResponse> listerRecapJournees(@PathVariable Long orgId) {
        Long membreId = requireMembreId();
        return recapJourneeService.listerJourneesPourMembre(orgId, membreId);
    }

    @GetMapping("/recap-journees/par-date")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public RecapMembreJourneeResponse recapJourneeParDate(
            @PathVariable Long orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return recapJourneeService.recapPourMembreParDate(orgId, date, requireMembreId());
    }

    @GetMapping("/recap-journees/{journeeId}")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public RecapMembreJourneeResponse recapJournee(
            @PathVariable Long orgId, @PathVariable Long journeeId) {
        return recapJourneeService.recapPourMembre(orgId, journeeId, requireMembreId());
    }

    @GetMapping("/demandes-en-attente")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<DemandeOperationMembreResponse> mesDemandesEnAttente(@PathVariable Long orgId) {
        return demandeOperationMembreService.mesDemandesEnAttente(orgId, requireMembreId());
    }

    @GetMapping("/mes-demandes")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<DemandeOperationMembreResponse> mesDemandes(@PathVariable Long orgId) {
        return demandeOperationMembreService.mesDemandesSuivi(orgId, requireMembreId());
    }

    @PostMapping("/operations/cotisation-hebdo")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public DemandeOperationMembreResponse cotisationHebdo(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationHebdoRequest request) {
        return demandeOperationMembreService.soumettreCotisationHebdo(orgId, requireMembreId(), request);
    }

    @PostMapping("/operations/cotisation-hebdo/preview")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<MouvementPreviewResponse> previewCotisationHebdo(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationHebdoRequest request) {
        request.setMembreId(requireMembreId());
        return operationMapperService.previewCotisationHebdo(orgId, request);
    }

    @PostMapping("/operations/cotisation-mois")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public DemandeOperationMembreResponse cotisationMois(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationMoisRequest request) {
        return demandeOperationMembreService.soumettreCotisationMois(orgId, requireMembreId(), request);
    }

    @PostMapping("/operations/cotisation-mois/preview")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public List<MouvementPreviewResponse> previewCotisationMois(
            @PathVariable Long orgId,
            @Valid @RequestBody CotisationMoisRequest request) {
        request.setMembreId(requireMembreId());
        return operationMapperService.previewCotisationMois(orgId, request);
    }

    @PostMapping("/emprunts/{empId}/rembourser")
    @PreAuthorize("hasRole('MEMBRE') and @orgSecurityService.belongsTo(#orgId)")
    public DemandeOperationMembreResponse rembourser(
            @PathVariable Long orgId,
            @PathVariable Long empId,
            @Valid @RequestBody RembourserRequest request) {
        return demandeOperationMembreService.soumettreRemboursement(orgId, requireMembreId(), empId, request);
    }

    private Long requireMembreId() {
        Long membreId = OrganisationContext.getMembreId();
        if (membreId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Compte membre non associé à cet utilisateur");
        }
        return membreId;
    }
}
