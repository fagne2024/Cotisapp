package com.cotisapp.controller;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.dto.response.SuiviMensuelResponse;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.service.SuiviMensuelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/organisations/{orgId}")
@RequiredArgsConstructor
public class SuiviMensuelController {

    private final SuiviMensuelService suiviMensuelService;
    private final MembreRepository membreRepository;

    @GetMapping("/suivi-mensuel")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<SuiviMensuelResponse> lister(
            @PathVariable Long orgId,
            @RequestParam String mois) {
        Map<Long, Membre> membres = membreRepository.findByOrganisationId(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));
        return suiviMensuelService.listerParMois(orgId, mois).stream()
                .map(s -> toResponse(s, membres.get(s.getMembreId())))
                .toList();
    }

    @PostMapping("/suivi-mensuel/generer")
    @PreAuthorize("@orgSecurityService.peutGestionOrg(#orgId)")
    public Map<String, Object> generer(@PathVariable Long orgId, @RequestParam String mois) {
        int created = suiviMensuelService.genererPourOrganisation(orgId, mois);
        return Map.of("mois", mois, "cree", created);
    }

    private SuiviMensuelResponse toResponse(SuiviMensuel s, Membre m) {
        return SuiviMensuelResponse.builder()
                .id(s.getId())
                .membreId(s.getMembreId())
                .membreNom(m != null ? m.getNomComplet() : null)
                .codeMembre(m != null ? m.getCodeMembre() : null)
                .moisAnnee(s.getMoisAnnee())
                .montantDu(s.getMontantDu())
                .montantPaye(s.getMontantPaye())
                .statut(s.getStatut())
                .build();
    }
}
