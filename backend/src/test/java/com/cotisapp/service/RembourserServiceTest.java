package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Echeance;
import com.cotisapp.domain.enums.*;
import com.cotisapp.dto.request.RembourserRequest;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.repository.EcheanceRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.security.OrganisationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RembourserServiceTest {

    @Mock private EmpruntRepository empruntRepository;
    @Mock private EcheanceRepository echeanceRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private RegleOperationRepository regleOperationRepository;
    @Mock private CompteService compteService;
    @Mock private JournalService journalService;
    @Mock private MembreRepository membreRepository;
    @Mock private ExerciceService exerciceService;
    @Mock private OperationPlanadGuardService operationPlanadGuardService;
    @Mock private OperationMemeJourControleService operationMemeJourControleService;

    private RembourserService rembourserService;

    private static final Long EXERCICE_ID = 1L;
    private static final Long ORG_ID = 1L;
    private static final Long EMPRUNT_ID = 10L;
    private static final Long MEMBRE_ID = 5L;

    @BeforeEach
    void setUp() {
        rembourserService = new RembourserService(
                empruntRepository,
                membreRepository,
                echeanceRepository,
                operationRepository,
                regleOperationRepository,
                compteService,
                journalService,
                exerciceService,
                operationPlanadGuardService,
                operationMemeJourControleService);
        OrganisationContext.set(ORG_ID, Role.ADMIN_GIE, 1L, null);
        lenient().when(exerciceService.requireExerciceCourantId(ORG_ID)).thenReturn(EXERCICE_ID);
        lenient().doNothing().when(exerciceService).verifierExerciceCourant(anyLong(), anyLong());
        mockRegles();
    }

    @AfterEach
    void tearDown() {
        OrganisationContext.clear();
    }

    @Test
    void rembourser_etale_debiteDepense_crediteCaisse() {
        Emprunt emprunt = empruntEtale();
        when(empruntRepository.findWithEcheancesByIdAndOrganisationId(EMPRUNT_ID, ORG_ID)).thenReturn(Optional.of(emprunt));
        when(echeanceRepository.findByIdAndEmpruntId(100L, EMPRUNT_ID)).thenReturn(Optional.of(echeance()));
        mockComptes();

        RembourserRequest req = new RembourserRequest();
        req.setEcheanceId(100L);
        req.setMontant(new BigDecimal("5000"));
        req.setDatePaiement(LocalDate.now());
        when(operationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rembourserService.rembourser(ORG_ID, EMPRUNT_ID, req);

        verify(compteService).appliquerMouvement(20L, SensMouvement.CREDIT, new BigDecimal("5000"));
        verify(compteService).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("5000"));
    }

    @Test
    void rembourser_solidarite_crediteSolidariteOrg() {
        Emprunt emprunt = Emprunt.builder()
                .id(EMPRUNT_ID).organisationId(ORG_ID).exerciceId(EXERCICE_ID).membreId(MEMBRE_ID)
                .typeEmprunt(TypeEmprunt.SOLIDARITE)
                .montantTotal(new BigDecimal("10000"))
                .montantRembourse(BigDecimal.ZERO)
                .statut(StatutEmprunt.EN_COURS)
                .dateCreation(LocalDate.now())
                .build();
        when(empruntRepository.findWithEcheancesByIdAndOrganisationId(EMPRUNT_ID, ORG_ID)).thenReturn(Optional.of(emprunt));
        mockComptes();
        RembourserRequest req = new RembourserRequest();
        req.setMontant(new BigDecimal("3000"));
        req.setDatePaiement(LocalDate.now());
        when(operationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rembourserService.rembourser(ORG_ID, EMPRUNT_ID, req);

        verify(compteService).appliquerMouvement(22L, SensMouvement.CREDIT, new BigDecimal("3000"));
        verify(compteService).appliquerMouvement(40L, SensMouvement.CREDIT, new BigDecimal("3000"));
    }

    @Test
    void rembourser_solidarite_avec_avance_caisse_ventile_caisse_et_solidarite() {
        Emprunt emprunt = Emprunt.builder()
                .id(EMPRUNT_ID).organisationId(ORG_ID).exerciceId(EXERCICE_ID).membreId(MEMBRE_ID)
                .typeEmprunt(TypeEmprunt.SOLIDARITE)
                .montantTotal(new BigDecimal("10000"))
                .montantRembourse(BigDecimal.ZERO)
                .montantAvanceCaisse(new BigDecimal("5000"))
                .montantRembourseAvanceCaisse(BigDecimal.ZERO)
                .statut(StatutEmprunt.EN_COURS)
                .dateCreation(LocalDate.now())
                .build();
        when(empruntRepository.findWithEcheancesByIdAndOrganisationId(EMPRUNT_ID, ORG_ID)).thenReturn(Optional.of(emprunt));
        mockComptes();
        RembourserRequest req = new RembourserRequest();
        req.setMontant(new BigDecimal("7000"));
        req.setDatePaiement(LocalDate.now());
        when(operationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rembourserService.rembourser(ORG_ID, EMPRUNT_ID, req);

        verify(compteService).appliquerMouvement(22L, SensMouvement.CREDIT, new BigDecimal("5000"));
        verify(compteService).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("5000"));
        verify(compteService, never()).appliquerMouvement(40L, SensMouvement.DEBIT, new BigDecimal("5000"));
        verify(compteService).appliquerMouvement(22L, SensMouvement.CREDIT, new BigDecimal("2000"));
        verify(compteService).appliquerMouvement(40L, SensMouvement.CREDIT, new BigDecimal("2000"));
        assertThat(emprunt.getMontantRembourseAvanceCaisse()).isEqualByComparingTo("5000");
    }

    @Test
    void rembourser_caisse_appliqueFraisSurCompteEpargneHebdo() {
        Emprunt emprunt = Emprunt.builder()
                .id(EMPRUNT_ID).organisationId(ORG_ID).exerciceId(EXERCICE_ID).membreId(MEMBRE_ID)
                .typeEmprunt(TypeEmprunt.CAISSE)
                .montantTotal(new BigDecimal("20000"))
                .montantRembourse(BigDecimal.ZERO)
                .statut(StatutEmprunt.EN_COURS)
                .dateCreation(LocalDate.now())
                .build();
        when(empruntRepository.findWithEcheancesByIdAndOrganisationId(EMPRUNT_ID, ORG_ID)).thenReturn(Optional.of(emprunt));
        mockComptes();
        RembourserRequest req = new RembourserRequest();
        req.setMontantCapital(new BigDecimal("8000"));
        req.setMontantFrais(new BigDecimal("200"));
        req.setDatePaiement(LocalDate.now());
        when(operationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rembourserService.rembourser(ORG_ID, EMPRUNT_ID, req);

        verify(compteService).appliquerMouvement(21L, SensMouvement.CREDIT, new BigDecimal("8000"));
        verify(compteService).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("8000"));
        verify(compteService).appliquerMouvement(21L, SensMouvement.CREDIT, new BigDecimal("200"));
        verify(compteService).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("200"));
        verify(compteService, never()).appliquerMouvement(21L, SensMouvement.DEBIT, new BigDecimal("200"));
        verify(compteService, never()).appliquerMouvement(60L, SensMouvement.CREDIT, new BigDecimal("200"));
    }

    @Test
    void rembourser_caisse_dernierPaiement_solde_transfereFraisVersCompteInteret() {
        Emprunt emprunt = Emprunt.builder()
                .id(EMPRUNT_ID).organisationId(ORG_ID).exerciceId(EXERCICE_ID).membreId(MEMBRE_ID)
                .typeEmprunt(TypeEmprunt.CAISSE)
                .montantTotal(new BigDecimal("10200"))
                .montantFrais(new BigDecimal("200"))
                .montantRembourse(new BigDecimal("10000"))
                .statut(StatutEmprunt.EN_COURS)
                .dateCreation(LocalDate.now())
                .build();
        when(empruntRepository.findWithEcheancesByIdAndOrganisationId(EMPRUNT_ID, ORG_ID)).thenReturn(Optional.of(emprunt));
        mockComptes();
        RembourserRequest req = new RembourserRequest();
        req.setMontantCapital(new BigDecimal("0"));
        req.setMontantFrais(new BigDecimal("200"));
        req.setDatePaiement(LocalDate.now());
        when(operationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rembourserService.rembourser(ORG_ID, EMPRUNT_ID, req);

        assertThat(emprunt.getStatut()).isEqualTo(StatutEmprunt.SOLDE);
        verify(compteService).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("200"));
        verify(compteService).appliquerMouvement(30L, SensMouvement.DEBIT, new BigDecimal("200"), false);
        verify(compteService).appliquerMouvement(60L, SensMouvement.CREDIT, new BigDecimal("200"));
    }

    @Test
    void rembourser_etale_avecPenaliteRetard_debiteMembreEtCrediteCaisse() {
        Emprunt emprunt = empruntEtale();
        Echeance ech = emprunt.getEcheances().get(0);
        ech.setDateEcheance(LocalDate.now().minusMonths(2));
        when(empruntRepository.findWithEcheancesByIdAndOrganisationId(EMPRUNT_ID, ORG_ID)).thenReturn(Optional.of(emprunt));
        when(echeanceRepository.findByIdAndEmpruntId(100L, EMPRUNT_ID)).thenReturn(Optional.of(ech));
        mockComptes();

        RembourserRequest req = new RembourserRequest();
        req.setEcheanceId(100L);
        req.setMontant(new BigDecimal("5000"));
        req.setDatePaiement(LocalDate.now());
        req.setAppliquerPenalite(true);
        req.setMontantPenalite(new BigDecimal("1000"));
        when(operationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rembourserService.rembourser(ORG_ID, EMPRUNT_ID, req);

        verify(compteService).appliquerMouvement(20L, SensMouvement.CREDIT, new BigDecimal("5000"));
        verify(compteService).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("5000"));
        verify(compteService).appliquerMouvement(20L, SensMouvement.CREDIT, new BigDecimal("1000"));
        verify(compteService).appliquerMouvement(50L, SensMouvement.CREDIT, new BigDecimal("1000"));
        verify(compteService, never()).appliquerMouvement(30L, SensMouvement.CREDIT, new BigDecimal("1000"));
    }

    private Emprunt empruntEtale() {
        Echeance ech = echeance();
        ech.setEmprunt(Emprunt.builder().id(EMPRUNT_ID).build());
        List<Echeance> echeances = new ArrayList<>();
        echeances.add(ech);
        return Emprunt.builder()
                .id(EMPRUNT_ID).organisationId(ORG_ID).exerciceId(EXERCICE_ID).membreId(MEMBRE_ID)
                .typeEmprunt(TypeEmprunt.ETALE)
                .montantTotal(new BigDecimal("50000"))
                .montantRembourse(BigDecimal.ZERO)
                .statut(StatutEmprunt.EN_COURS)
                .dateCreation(LocalDate.now())
                .echeances(echeances)
                .build();
    }

    private Echeance echeance() {
        return Echeance.builder()
                .id(100L).numero(1)
                .montantEcheance(new BigDecimal("5000"))
                .montantPaye(BigDecimal.ZERO)
                .dateEcheance(LocalDate.now().plusMonths(1))
                .statut(StatutEcheance.A_PAYER)
                .build();
    }

    private void mockRegles() {
        RegleOperation etale = RegleOperation.builder()
                .id(1L).organisationId(ORG_ID)
                .typeOperation(TypeOperation.EMPRUNT)
                .libelle("Emprunt étalé")
                .typePenalite(TypeModeCalcul.FIXE)
                .montantPenalite(new BigDecimal("500"))
                .actif(true)
                .build();
        RegleOperation caisse = RegleOperation.builder()
                .id(2L).organisationId(ORG_ID)
                .typeOperation(TypeOperation.EMPRUNT)
                .libelle("Emprunt caisse")
                .typePenalite(TypeModeCalcul.POURCENTAGE)
                .pourcentagePenalite(new BigDecimal("2"))
                .actif(true)
                .build();
        RegleOperation solidarite = RegleOperation.builder()
                .id(3L).organisationId(ORG_ID)
                .typeOperation(TypeOperation.EMPRUNT)
                .libelle("Emprunt solidarité")
                .typePenalite(TypeModeCalcul.FIXE)
                .montantPenalite(new BigDecimal("200"))
                .actif(true)
                .build();
        lenient().when(regleOperationRepository.findByOrganisationId(ORG_ID))
                .thenReturn(List.of(etale, caisse, solidarite));
    }

    private void mockComptes() {
        lenient().when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.EPARGNE_MOIS)))
                .thenReturn(Compte.builder().id(20L).membreId(MEMBRE_ID).typeCompte(TypeCompte.EPARGNE_MOIS).build());
        lenient().when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.EPARGNE_HEBDO)))
                .thenReturn(Compte.builder().id(21L).membreId(MEMBRE_ID).typeCompte(TypeCompte.EPARGNE_HEBDO).build());
        lenient().when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.SOLIDARITE)))
                .thenReturn(Compte.builder().id(22L).membreId(MEMBRE_ID).typeCompte(TypeCompte.SOLIDARITE).build());
        lenient().when(compteService.getCompteOrg(eq(ORG_ID), eq(TypeCompte.CAISSE)))
                .thenReturn(Compte.builder().id(30L).organisationId(ORG_ID).typeCompte(TypeCompte.CAISSE).build());
        lenient().when(compteService.getCompteOrg(eq(ORG_ID), eq(TypeCompte.SOLIDARITE)))
                .thenReturn(Compte.builder().id(40L).organisationId(ORG_ID).typeCompte(TypeCompte.SOLIDARITE).build());
        lenient().when(compteService.ensureCompteOrganisationInteret(ORG_ID))
                .thenReturn(Compte.builder().id(60L).organisationId(ORG_ID).typeCompte(TypeCompte.INTERET).build());
        lenient().when(compteService.ensureCompteOrganisationAmendes(ORG_ID))
                .thenReturn(Compte.builder().id(50L).organisationId(ORG_ID).typeCompte(TypeCompte.AMENDES).build());
    }
}
