package com.cotisapp.service;

import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.security.OrganisationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoteurOperationServiceTest {

    @Mock private OperationRepository operationRepository;
    @Mock private RegleOperationRepository regleOperationRepository;
    @Mock private MembreRepository membreRepository;
    @Mock private CompteService compteService;
    @Mock private SuiviMensuelService suiviMensuelService;
    @Mock private JournalService journalService;
    @Mock private CotisationRegleExecutor cotisationRegleExecutor;
    @Mock private CotisationAmendeHelper cotisationAmendeHelper;
    @Mock private ExerciceService exerciceService;
    @Mock private OperationPlanadGuardService operationPlanadGuardService;
    @Mock private OperationMemeJourControleService operationMemeJourControleService;

    private MoteurOperationService moteurOperationService;

    @BeforeEach
    void setUp() {
        moteurOperationService = new MoteurOperationService(
                operationRepository,
                regleOperationRepository,
                membreRepository,
                suiviMensuelService,
                journalService,
                cotisationRegleExecutor,
                cotisationAmendeHelper,
                exerciceService,
                operationPlanadGuardService,
                operationMemeJourControleService);
        OrganisationContext.set(1L, Role.ADMIN_GIE, 1L, null);
    }

    @AfterEach
    void tearDown() {
        OrganisationContext.clear();
    }

    @Test
    void cotisationMois_crediteEpargneEtCaisse() {
        Long orgId = 1L;
        Long membreId = 3L;
        Membre membre = Membre.builder().id(membreId).organisationId(orgId)
                .codeMembre("GDR-003").prenom("Aïda").nom("Ndiaye").build();
        RegleOperation regle = RegleOperation.builder()
                .organisationId(orgId)
                .typeOperation(TypeOperation.COTISATION_MOIS)
                .montantMin(new BigDecimal("5000"))
                .montantMax(new BigDecimal("20000"))
                .solidariteAuto(false)
                .build();

        when(exerciceService.requireExerciceCourantId(orgId)).thenReturn(1L);
        when(membreRepository.findByIdAndOrganisationId(membreId, orgId)).thenReturn(Optional.of(membre));
        when(regleOperationRepository.findByOrganisationIdAndTypeOperationAndActifTrue(orgId, TypeOperation.COTISATION_MOIS))
                .thenReturn(Optional.of(regle));
        when(cotisationRegleExecutor.executer(eq(orgId), eq(membreId), any(), eq(regle), any()))
                .thenReturn(Collections.emptyList());
        when(cotisationAmendeHelper.appliquer(eq(orgId), eq(membreId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(operationRepository.save(any())).thenAnswer(i -> {
            var op = i.getArgument(0, com.cotisapp.domain.entity.Operation.class);
            op.setId(99L);
            return op;
        });

        CotisationMoisRequest req = new CotisationMoisRequest();
        req.setMembreId(membreId);
        req.setMoisAnnee("2026-05");
        req.setMontant(new BigDecimal("8000"));
        req.setDateOperation(LocalDate.of(2026, 5, 14));

        var result = moteurOperationService.cotisationMois(orgId, req);

        assertThat(result.getMontant()).isEqualByComparingTo("8000");
        verify(cotisationRegleExecutor).executer(eq(orgId), eq(membreId), any(), eq(regle), eq(new BigDecimal("8000")));
        verify(cotisationAmendeHelper).valider(orgId, null, regle);
        verify(suiviMensuelService).mettreAJourApresPaiement(orgId, membreId, "2026-05", new BigDecimal("8000"));
    }
}
