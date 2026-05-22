package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.domain.enums.StatutSuiviMensuel;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.repository.SuiviMensuelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuiviMensuelService {

    private static final DateTimeFormatter MOIS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SuiviMensuelRepository suiviMensuelRepository;
    private final MembreRepository membreRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final ExerciceService exerciceService;

    @Transactional
    public int genererPourOrganisation(Long orgId, String moisAnnee) {
        int created = 0;
        for (Membre m : membreRepository.findByOrganisationIdAndActifTrue(orgId)) {
            if (genererPourMembre(orgId, m.getId(), moisAnnee)) {
                created++;
            }
        }
        return created;
    }

    @Transactional
    public boolean genererPourMembre(Long orgId, Long membreId, String moisAnnee) {
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        if (suiviMensuelRepository.existsByExerciceIdAndMembreIdAndMoisAnnee(exerciceId, membreId, moisAnnee)) {
            return false;
        }
        BigDecimal montantDu = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION_MOIS)
                .map(r -> r.getMontantMin() != null ? r.getMontantMin() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);

        suiviMensuelRepository.save(SuiviMensuel.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .membreId(membreId)
                .moisAnnee(moisAnnee)
                .montantDu(montantDu)
                .montantPaye(BigDecimal.ZERO)
                .statut(StatutSuiviMensuel.NON_PAYE)
                .build());
        return true;
    }

    @Transactional
    public void mettreAJourApresPaiement(Long orgId, Long membreId, String moisAnnee, BigDecimal montant) {
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        SuiviMensuel suivi = suiviMensuelRepository.findByExerciceIdAndMembreIdAndMoisAnnee(exerciceId, membreId, moisAnnee)
                .orElseGet(() -> {
                    genererPourMembre(orgId, membreId, moisAnnee);
                    return suiviMensuelRepository.findByExerciceIdAndMembreIdAndMoisAnnee(exerciceId, membreId, moisAnnee)
                            .orElseThrow();
                });
        suivi.setMontantPaye(suivi.getMontantPaye().add(montant));
        if (suivi.getMontantPaye().compareTo(suivi.getMontantDu()) >= 0) {
            suivi.setStatut(StatutSuiviMensuel.PAYE);
        } else if (suivi.getMontantPaye().compareTo(BigDecimal.ZERO) > 0) {
            suivi.setStatut(StatutSuiviMensuel.PARTIEL);
        }
        suivi.setDatePaiement(LocalDate.now());
        suiviMensuelRepository.save(suivi);
    }

    public List<SuiviMensuel> listerParMois(Long orgId, String mois) {
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        return suiviMensuelRepository.findByOrganisationIdAndExerciceIdAndMoisAnnee(orgId, exerciceId, mois);
    }
}
