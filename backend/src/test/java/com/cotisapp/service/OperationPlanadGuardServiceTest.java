package com.cotisapp.service;

import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.JourneeReunionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationPlanadGuardServiceTest {

    private static final Long EXERCICE_ID = 1L;
    private static final Long ORG_ID = 10L;

    @Mock
    private JourneeReunionRepository journeeReunionRepository;

    @Mock
    private PlanadOuvertureService planadOuvertureService;

    @InjectMocks
    private OperationPlanadGuardService guardService;

    @Test
    void remboursement_autorise_date_differente_du_planad_ouvert() {
        LocalDate datePaiement = LocalDate.of(2026, 5, 21);

        when(journeeReunionRepository.findByExerciceIdAndDateReunion(EXERCICE_ID, datePaiement))
                .thenReturn(Optional.empty());

        assertThatCode(() ->
                        guardService.verifierDateOperationAutorisee(ORG_ID, EXERCICE_ID, datePaiement, false))
                .doesNotThrowAnyException();
    }

    @Test
    void cotisation_refuse_date_differente_du_planad_ouvert() {
        LocalDate dateOp = LocalDate.of(2026, 5, 21);
        LocalDate datePlanadOuvert = LocalDate.of(2026, 5, 6);

        when(journeeReunionRepository.findByExerciceIdAndDateReunion(EXERCICE_ID, dateOp))
                .thenReturn(Optional.empty());
        when(journeeReunionRepository.findPlanadOuvert(EXERCICE_ID))
                .thenReturn(Optional.of(planad("PLANAS n°1", datePlanadOuvert, StatutPlanad.OUVERT)));

        assertThatThrownBy(() ->
                        guardService.verifierDateOperationAutorisee(ORG_ID, EXERCICE_ID, dateOp, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Clôturez d'abord");
    }

    @Test
    void remboursement_refuse_date_sur_planad_cloture() {
        LocalDate datePaiement = LocalDate.of(2026, 5, 6);

        when(journeeReunionRepository.findByExerciceIdAndDateReunion(EXERCICE_ID, datePaiement))
                .thenReturn(Optional.of(planad("PLANAS n°1", datePaiement, StatutPlanad.CLOTURE)));

        assertThatThrownBy(() ->
                        guardService.verifierDateOperationAutorisee(ORG_ID, EXERCICE_ID, datePaiement, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clôturé");
    }

    private static JourneeReunion planad(String libelle, LocalDate date, StatutPlanad statut) {
        return JourneeReunion.builder()
                .libelle(libelle)
                .dateReunion(date)
                .statut(statut)
                .build();
    }
}
