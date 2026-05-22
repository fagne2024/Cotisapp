package com.cotisapp.controller;

import com.cotisapp.dto.request.CreateMembreRequest;
import com.cotisapp.dto.request.UpdateMembreRequest;
import com.cotisapp.dto.response.CompteMembreResponse;
import com.cotisapp.dto.response.ImportMembresResponse;
import com.cotisapp.dto.response.MembreResponse;
import com.cotisapp.dto.response.MembreSoldeMembreResponse;
import com.cotisapp.dto.response.MembreSoldesResponse;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.service.MembreFicheService;
import com.cotisapp.service.MembreImportService;
import com.cotisapp.service.MembreService;
import com.cotisapp.service.RapportMembreService;
import com.cotisapp.dto.response.RapportMembreResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.repository.MembreRepository;

import java.util.List;

@RestController
@RequestMapping("/api/organisations/{orgId}/membres")
@RequiredArgsConstructor
public class MembreController {

    private final MembreRepository membreRepository;
    private final MembreService membreService;
    private final MembreImportService membreImportService;
    private final MembreFicheService membreFicheService;
    private final RapportMembreService rapportMembreService;

    @GetMapping
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<MembreResponse> lister(
            @PathVariable Long orgId,
            @RequestParam(value = "tous", required = false, defaultValue = "false") boolean tous) {
        List<Membre> source = tous
                ? membreRepository.findByOrganisationId(orgId)
                : membreRepository.findByOrganisationIdAndActifTrue(orgId);
        return source.stream().map(membreService::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public MembreResponse creer(@PathVariable Long orgId, @Valid @RequestBody CreateMembreRequest request) {
        return membreService.creer(orgId, request);
    }

    @PutMapping("/{membreId}")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public MembreResponse modifier(
            @PathVariable Long orgId,
            @PathVariable Long membreId,
            @Valid @RequestBody UpdateMembreRequest request) {
        return membreService.modifier(orgId, membreId, request);
    }

    @DeleteMapping("/{membreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public void supprimer(@PathVariable Long orgId, @PathVariable Long membreId) {
        membreService.supprimer(orgId, membreId);
    }

    @GetMapping("/recherche")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<MembreResponse> rechercher(
            @PathVariable Long orgId,
            @RequestParam("q") String q) {
        return membreService.rechercher(orgId, q);
    }

    @GetMapping("/soldes-comptes")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public List<MembreSoldesResponse> listerSoldesComptes(@PathVariable Long orgId) {
        return membreFicheService.listerSoldesComptes(orgId);
    }

    @GetMapping("/import/modele")
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public ResponseEntity<Resource> telechargerModeleImport(@PathVariable Long orgId) {
        byte[] contenu = membreImportService.genererModele();
        ByteArrayResource resource = new ByteArrayResource(contenu);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-import-membres.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(contenu.length)
                .body(resource);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("""
        @orgSecurityService.peutGestionOrg(#orgId)
        """)
    public ImportMembresResponse importer(
            @PathVariable Long orgId, @RequestParam("fichier") MultipartFile fichier) {
        return membreImportService.importer(orgId, fichier);
    }

    @GetMapping("/{membreId}/solde")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canViewMembre(#orgId, #membreId)")
    public MembreSoldeMembreResponse obtenirSoldeMembre(
            @PathVariable Long orgId, @PathVariable Long membreId) {
        return membreFicheService.calculerSoldeMembre(orgId, membreId);
    }

    @GetMapping("/{membreId}/comptes")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canViewMembre(#orgId, #membreId)")
    public List<CompteMembreResponse> listerComptes(
            @PathVariable Long orgId, @PathVariable Long membreId) {
        return membreFicheService.listerComptes(orgId, membreId);
    }

    @GetMapping("/{membreId}/operations")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canViewMembre(#orgId, #membreId)")
    public List<OperationResponse> listerOperations(
            @PathVariable Long orgId, @PathVariable Long membreId) {
        return membreFicheService.listerOperations(orgId, membreId);
    }

    @GetMapping("/{membreId}/rapport")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canViewMembre(#orgId, #membreId)")
    public RapportMembreResponse rapportMembre(
            @PathVariable Long orgId,
            @PathVariable Long membreId,
            @RequestParam(required = false) String periode) {
        return rapportMembreService.generer(orgId, membreId, periode);
    }

    @GetMapping("/{membreId}")
    @PreAuthorize("hasRole('SUPERADMIN') or @orgSecurityService.canViewMembre(#orgId, #membreId)")
    public MembreResponse get(@PathVariable Long orgId, @PathVariable Long membreId) {
        return membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .map(membreService::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }
}
