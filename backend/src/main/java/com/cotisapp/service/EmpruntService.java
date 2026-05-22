package com.cotisapp.service;

import com.cotisapp.domain.entity.Echeance;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.dto.response.EcheanceResponse;
import com.cotisapp.dto.response.EmpruntResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpruntService {

    private static final List<StatutEmprunt> STATUTS_SUIVI =
            List.copyOf(EnumSet.of(StatutEmprunt.EN_COURS, StatutEmprunt.SOLDE));

    private final EmpruntRepository empruntRepository;
    private final MembreRepository membreRepository;
    private final ExerciceService exerciceService;

    /** Emprunts actifs (EN_COURS) — octroi, remboursements, tableau de bord. */
    @Transactional(readOnly = true)
    public List<EmpruntResponse> lister(Long orgId, TypeEmprunt type) {
        Map<Long, Membre> membres = membreRepository.findByOrganisationId(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));
        List<Emprunt> emprunts = type == null
                ? empruntRepository.findByOrganisationIdAndStatut(orgId, StatutEmprunt.EN_COURS)
                : empruntRepository.findByOrganisationIdAndStatutAndTypeEmprunt(orgId, StatutEmprunt.EN_COURS, type);
        return emprunts.stream()
                .map(e -> toResponse(e, membres.get(e.getMembreId())))
                .toList();
    }

    /** Suivi : emprunts en cours et soldés (hors annulés), tous exercices. */
    @Transactional(readOnly = true)
    public List<EmpruntResponse> listerPourSuivi(Long orgId, TypeEmprunt type) {
        Map<Long, Membre> membres = membreRepository.findByOrganisationId(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));
        List<Emprunt> emprunts = type == null
                ? empruntRepository.findByOrganisationIdAndStatutIn(orgId, STATUTS_SUIVI)
                : empruntRepository.findByOrganisationIdAndStatutInAndTypeEmprunt(orgId, STATUTS_SUIVI, type);
        return emprunts.stream()
                .map(e -> toResponse(e, membres.get(e.getMembreId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmpruntResponse> listerPourSuiviParMembre(Long orgId, Long membreId) {
        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        return empruntRepository.findByMembreIdAndOrganisationId(membreId, orgId).stream()
                .filter(e -> STATUTS_SUIVI.contains(e.getStatut()))
                .map(e -> toResponse(e, membre))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmpruntResponse> listerParMembre(Long orgId, Long membreId) {
        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        return empruntRepository.findByMembreIdAndOrganisationId(membreId, orgId).stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS || exerciceId.equals(e.getExerciceId()))
                .map(e -> toResponse(e, membre))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmpruntResponse getById(Long orgId, Long empruntId) {
        Emprunt emprunt = empruntRepository.findWithEcheancesByIdAndOrganisationId(empruntId, orgId)
                .orElseThrow(() -> new BusinessException("Emprunt introuvable"));
        Membre membre = membreRepository.findById(emprunt.getMembreId()).orElse(null);
        return toResponse(emprunt, membre);
    }

    private EmpruntResponse toResponse(Emprunt e, Membre membre) {
        BigDecimal restant = e.getMontantTotal().subtract(e.getMontantRembourse());
        return EmpruntResponse.builder()
                .id(e.getId())
                .membreId(e.getMembreId())
                .membreNom(membre != null ? membre.getNomComplet() : null)
                .codeMembre(membre != null ? membre.getCodeMembre() : null)
                .typeEmprunt(e.getTypeEmprunt())
                .montantTotal(e.getMontantTotal())
                .montantRembourse(e.getMontantRembourse())
                .montantRestant(restant.max(BigDecimal.ZERO))
                .montantFrais(e.getMontantFrais())
                .montantAvanceCaisse(e.getMontantAvanceCaisse())
                .montantRembourseAvanceCaisse(e.getMontantRembourseAvanceCaisse())
                .montantAvanceCaisseRestant(EmpruntAvanceCaisseHelper.avanceCaisseRestant(e))
                .statut(e.getStatut())
                .dateCreation(e.getDateCreation())
                .echeances(e.getEcheances().stream().map(this::toEcheance).toList())
                .build();
    }

    private EcheanceResponse toEcheance(Echeance ech) {
        return EcheanceResponse.builder()
                .id(ech.getId())
                .numero(ech.getNumero())
                .montantEcheance(ech.getMontantEcheance())
                .montantPaye(ech.getMontantPaye())
                .dateEcheance(ech.getDateEcheance())
                .statut(ech.getStatut())
                .build();
    }
}
