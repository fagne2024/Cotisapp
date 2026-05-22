package com.cotisapp.service;

import com.cotisapp.domain.entity.Exercice;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.enums.StatutExercice;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.ExerciceRepository;
import com.cotisapp.repository.JourneeReunionRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciceReouvertureServiceTest {

    @Mock
    private ExerciceRepository exerciceRepository;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private JourneeReunionRepository journeeReunionRepository;
    @Mock
    private CompteRepository compteRepository;
    @Mock
    private EmpruntRepository empruntRepository;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private JournalService journalService;

    @InjectMocks
    private ExerciceService exerciceService;

    @Test
    void reouvrir_remplaceExerciceCourant() {
        Long orgId = 1L;
        Exercice cible = Exercice.builder()
                .id(10L)
                .organisationId(orgId)
                .numero(1)
                .statut(StatutExercice.CLOTURE)
                .build();
        Exercice enCours = Exercice.builder()
                .id(20L)
                .organisationId(orgId)
                .numero(2)
                .statut(StatutExercice.EN_COURS)
                .build();
        Organisation org = Organisation.builder().id(orgId).exerciceCourantId(20L).build();

        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(exerciceRepository.findByIdAndOrganisationId(10L, orgId)).thenReturn(Optional.of(cible));
        when(exerciceRepository.findByOrganisationIdAndStatut(orgId, StatutExercice.EN_COURS))
                .thenReturn(Optional.of(enCours));
        when(operationRepository.countByOrganisationIdAndExerciceId(orgId, 20L)).thenReturn(0L);
        when(journeeReunionRepository.findMaxNumero(20L)).thenReturn(0);
        when(empruntRepository.findByOrganisationIdAndExerciceId(orgId, 20L)).thenReturn(List.of());
        when(exerciceRepository.save(any(Exercice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(journeeReunionRepository.countByExerciceIdAndStatut(10L, com.cotisapp.domain.enums.StatutPlanad.OUVERT))
                .thenReturn(0L);
        when(journeeReunionRepository.findPlanadOuvert(10L)).thenReturn(Optional.empty());
        when(journeeReunionRepository.findMaxNumero(10L)).thenReturn(5);

        var response = exerciceService.reouvrir(orgId, 10L);

        assertThat(response.getNumero()).isEqualTo(1);
        assertThat(response.isCourant()).isTrue();
        assertThat(cible.getStatut()).isEqualTo(StatutExercice.EN_COURS);
        assertThat(enCours.getStatut()).isEqualTo(StatutExercice.CLOTURE);
        verify(organisationRepository).save(org);
        assertThat(org.getExerciceCourantId()).isEqualTo(10L);
    }
}
