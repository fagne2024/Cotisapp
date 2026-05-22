package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Exercice;
import com.cotisapp.domain.entity.Organisation;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.StatutExercice;
import com.cotisapp.domain.enums.StatutPlanad;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.dto.request.OuvrirExerciceRequest;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciceServiceTest {

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
    void cloturerEtOuvrirSuivant_reinitialiseLesSoldes() {
        Long orgId = 1L;
        Exercice courant = Exercice.builder()
                .id(10L)
                .organisationId(orgId)
                .numero(1)
                .statut(StatutExercice.EN_COURS)
                .build();
        Organisation org = Organisation.builder().id(orgId).exerciceCourantId(10L).build();
        Compte compte = Compte.builder()
                .organisationId(orgId)
                .typeCompte(TypeCompte.CAISSE)
                .proprietaire(ProprietaireCompte.ORGANISATION)
                .solde(new BigDecimal("5000"))
                .build();

        when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(exerciceRepository.findById(10L)).thenReturn(Optional.of(courant));
        when(exerciceRepository.findByIdAndOrganisationId(10L, orgId)).thenReturn(Optional.of(courant));
        when(empruntRepository.findByOrganisationId(orgId)).thenReturn(List.of());
        when(journeeReunionRepository.existsByExerciceIdAndStatut(10L, StatutPlanad.OUVERT)).thenReturn(false);
        when(journeeReunionRepository.findMaxNumero(10L)).thenReturn(12);
        when(journeeReunionRepository.findMaxNumero(20L)).thenReturn(0);
        when(journeeReunionRepository.countByExerciceIdAndStatut(20L, StatutPlanad.OUVERT)).thenReturn(0L);
        when(journeeReunionRepository.findPlanadOuvert(20L)).thenReturn(Optional.empty());
        when(exerciceRepository.save(any(Exercice.class))).thenAnswer(inv -> {
            Exercice e = inv.getArgument(0);
            if (e.getId() == null && e.getNumero() == 2) {
                e.setId(20L);
            }
            return e;
        });
        when(exerciceRepository.findMaxNumero(orgId)).thenReturn(1);
        when(compteRepository.findByOrganisationId(orgId)).thenReturn(List.of(compte));
        when(compteRepository.saveAll(any())).thenReturn(List.of(compte));

        OuvrirExerciceRequest req = new OuvrirExerciceRequest();
        req.setReinitialiserComptes(true);
        var response = exerciceService.cloturerEtOuvrirSuivant(orgId, req);

        assertThat(response.getNumero()).isEqualTo(2);
        assertThat(response.isCourant()).isTrue();
        assertThat(courant.getStatut()).isEqualTo(StatutExercice.CLOTURE);
        assertThat(courant.getPlanadFin()).isEqualTo(12);
        assertThat(compte.getSolde()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(organisationRepository).save(org);
        assertThat(org.getExerciceCourantId()).isEqualTo(20L);
    }
}
