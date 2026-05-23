package com.cotisapp.service;

import com.cotisapp.domain.entity.JournalAudit;
import com.cotisapp.domain.enums.TypeEvenementJournal;
import com.cotisapp.dto.request.EnregistrerEvenementJournalRequest;
import com.cotisapp.dto.response.JournalUtilisateurResponse;
import com.cotisapp.repository.JournalAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JournalUtilisateurService {

    private final JournalAuditRepository journalAuditRepository;
    private final JournalService journalService;

    @Transactional(readOnly = true)
    public Page<JournalUtilisateurResponse> lister(
            Long orgId,
            Long utilisateurId,
            TypeEvenementJournal type,
            Boolean succes,
            String search,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return journalAuditRepository
                .rechercherPourOrganisation(orgId, utilisateurId, type, succes, normaliserSearch(search), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long compter(Long orgId) {
        return journalAuditRepository.countByOrganisationId(orgId);
    }

    @Transactional
    public void enregistrerEvenementClient(
            Long orgId, EnregistrerEvenementJournalRequest request, HttpServletRequest httpRequest) {
        if (request.getTypeEvenement() == TypeEvenementJournal.MODULE_VISITE
                || request.getTypeEvenement() == TypeEvenementJournal.NAVIGATION) {
            journalService.enregistrerVisiteModule(
                    orgId,
                    request.getModuleCode(),
                    request.getModuleLibelle(),
                    request.getRoutePath(),
                    request.getDetails(),
                    httpRequest);
            return;
        }
        String action = request.getAction();
        journalService.enregistrer(JournalAudit.builder()
                .organisationId(orgId)
                .action(action)
                .typeEvenement(request.getTypeEvenement())
                .moduleCode(request.getModuleCode())
                .moduleLibelle(request.getModuleLibelle())
                .routePath(request.getRoutePath())
                .details(JournalAuditLibelleFormatter.enrichirDetailsAction(action, request.getDetails()))
                .ipAddress(JournalService.extraireIp(httpRequest))
                .userAgent(JournalService.tronquer(
                        httpRequest != null ? httpRequest.getHeader("User-Agent") : null, 500))
                .succes(request.getSucces() == null || request.getSucces()));
    }

    private JournalUtilisateurResponse toResponse(JournalAudit j) {
        return JournalUtilisateurResponse.builder()
                .id(j.getId())
                .organisationId(j.getOrganisationId())
                .utilisateurId(j.getUtilisateurId())
                .utilisateurEmail(j.getUtilisateurEmail())
                .utilisateurNom(j.getUtilisateurNom())
                .role(j.getRole())
                .membreId(j.getMembreId())
                .action(j.getAction())
                .typeEvenement(j.getTypeEvenement())
                .typeEvenementLibelle(JournalAuditLibelleFormatter.libelleTypeCourt(j.getTypeEvenement()))
                .moduleCode(j.getModuleCode())
                .moduleLibelle(j.getModuleLibelle())
                .routePath(j.getRoutePath())
                .details(JournalAuditLibelleFormatter.detailAffichage(j))
                .ipAddress(j.getIpAddress())
                .userAgent(j.getUserAgent())
                .navigateurResume(JournalAuditLibelleFormatter.resumeNavigateur(j.getUserAgent()))
                .succes(j.getSucces())
                .dateCreation(j.getDateCreation())
                .libelleResume(JournalAuditLibelleFormatter.libelleResume(j))
                .build();
    }

    private static String normaliserSearch(String search) {
        if (search == null) {
            return null;
        }
        String s = search.trim();
        return s.isEmpty() ? null : s;
    }
}
