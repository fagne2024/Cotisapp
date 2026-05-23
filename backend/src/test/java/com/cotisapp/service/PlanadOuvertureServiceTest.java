package com.cotisapp.service;

import com.cotisapp.domain.entity.JourneeReunion;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.repository.JourneeReunionRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanadOuvertureServiceTest {

    private static final Long ORG_ID = 10L;
    private static final Long EXERCICE_ID = 1L;

    @Mock
    private JourneeReunionRepository journeeReunionRepository;

    @Mock
    private OperationRepository operationRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @InjectMocks
    private PlanadOuvertureService service;

    @Test
    void ouvrirPlanadSuivantApresCloture_utiliseLaDateDeLaPremiereOperationPosterieure() {
        LocalDate dateCloturee = LocalDate.of(2026, 5, 6);
        LocalDate dateSuivante = LocalDate.of(2026, 5, 13);

        when(journeeReunionRepository.findPlanadOuvert(EXERCICE_ID)).thenReturn(Optional.empty());
        when(operationRepository.findMinDateOperationApres(ORG_ID, EXERCICE_ID, dateCloturee))
                .thenReturn(Optional.of(dateSuivante));
        when(journeeReunionRepository.findByExerciceIdAndDateReunion(EXERCICE_ID, dateSuivante))
                .thenReturn(Optional.empty());
        when(organisationRepository.findById(ORG_ID))
                .thenReturn(Optional.of(Organisation.builder().id(ORG_ID).code("GDR").build()));
        when(journeeReunionRepository.findMaxNumero(EXERCICE_ID)).thenReturn(2);
        when(journeeReunionRepository.save(any(JourneeReunion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Optional<JourneeReunion> result =
                service.ouvrirPlanadSuivantApresCloture(ORG_ID, EXERCICE_ID, dateCloturee);

        assertThat(result).isPresent();
        ArgumentCaptor<JourneeReunion> captor = ArgumentCaptor.forClass(JourneeReunion.class);
        verify(journeeReunionRepository).save(captor.capture());
        assertThat(captor.getValue().getDateReunion()).isEqualTo(dateSuivante);
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutPlanad.OUVERT);
        assertThat(captor.getValue().getNumero()).isEqualTo(3);
    }

    @Test
    void ouvrirPlanadSuivantApresCloture_rienSiAucuneOperationFuture() {
        LocalDate dateCloturee = LocalDate.of(2026, 5, 6);

        when(journeeReunionRepository.findPlanadOuvert(EXERCICE_ID)).thenReturn(Optional.empty());
        when(operationRepository.findMinDateOperationApres(ORG_ID, EXERCICE_ID, dateCloturee))
                .thenReturn(Optional.empty());

        Optional<JourneeReunion> result =
                service.ouvrirPlanadSuivantApresCloture(ORG_ID, EXERCICE_ID, dateCloturee);

        assertThat(result).isEmpty();
        verify(journeeReunionRepository, never()).save(any());
    }
}
