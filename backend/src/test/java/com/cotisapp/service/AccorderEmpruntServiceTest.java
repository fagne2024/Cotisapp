package com.cotisapp.service;



import com.cotisapp.domain.entity.Compte;

import com.cotisapp.domain.entity.Membre;

import com.cotisapp.domain.entity.RegleOperation;

import com.cotisapp.domain.enums.*;

import com.cotisapp.dto.request.AccorderEmpruntRequest;

import com.cotisapp.exception.BusinessException;

import com.cotisapp.repository.EmpruntRepository;

import com.cotisapp.repository.MembreRepository;

import com.cotisapp.repository.OperationRepository;

import com.cotisapp.repository.RegleOperationRepository;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;

import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;



import java.math.BigDecimal;

import java.time.LocalDate;

import java.util.List;

import java.util.Optional;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.*;

import static org.mockito.Mockito.*;



@ExtendWith(MockitoExtension.class)

class AccorderEmpruntServiceTest {



    private static final Long ORG_ID = 1L;

    private static final Long MEMBRE_ID = 5L;



    @Mock

    private MembreRepository membreRepository;

    @Mock

    private RegleOperationRepository regleOperationRepository;

    @Mock

    private EmpruntRepository empruntRepository;

    @Mock

    private OperationRepository operationRepository;

    @Mock

    private CompteService compteService;

    @Mock

    private JournalService journalService;

    @Mock

    private EmpruntService empruntService;

    @Mock

    private ExerciceService exerciceService;

    @Mock
    private OperationPlanadGuardService operationPlanadGuardService;

    @Mock
    private OperationMemeJourControleService operationMemeJourControleService;

    private AccorderEmpruntService accorderEmpruntService;

    private Compte caisse;

    private Compte solidarite;



    @BeforeEach

    void setUp() {

        accorderEmpruntService = new AccorderEmpruntService(
                membreRepository,
                regleOperationRepository,
                empruntRepository,
                operationRepository,
                compteService,
                journalService,
                empruntService,
                exerciceService,
                operationPlanadGuardService,
                operationMemeJourControleService);

        caisse = Compte.builder().id(10L).organisationId(ORG_ID).typeCompte(TypeCompte.CAISSE)

                .proprietaire(ProprietaireCompte.ORGANISATION).solde(new BigDecimal("100000")).build();

        solidarite = Compte.builder().id(11L).organisationId(ORG_ID).typeCompte(TypeCompte.SOLIDARITE)

                .proprietaire(ProprietaireCompte.ORGANISATION).solde(new BigDecimal("5000")).build();

    }



    @Test

    void calculerAvanceCaisse_partie_non_couverte_par_solde_positif() {

        assertThat(AccorderEmpruntService.calculerAvanceCaisseVersSolidarite(

                new BigDecimal("5000"), new BigDecimal("10000")))

                .isEqualByComparingTo("5000");

        assertThat(AccorderEmpruntService.calculerAvanceCaisseVersSolidarite(

                new BigDecimal("10000"), new BigDecimal("8000")))

                .isEqualByComparingTo("0");

    }



    @Test
    void accorder_refuse_si_membre_a_deja_un_emprunt_en_cours_meme_type() {
        when(membreRepository.findByIdAndOrganisationId(MEMBRE_ID, ORG_ID))
                .thenReturn(Optional.of(Membre.builder().id(MEMBRE_ID).organisationId(ORG_ID).build()));
        when(empruntRepository.existsByMembreIdAndOrganisationIdAndStatutAndTypeEmprunt(
                        MEMBRE_ID, ORG_ID, StatutEmprunt.EN_COURS, TypeEmprunt.CAISSE))
                .thenReturn(true);

        AccorderEmpruntRequest req = request(TypeEmprunt.CAISSE, new BigDecimal("5000"));

        assertThatThrownBy(() -> accorderEmpruntService.accorder(ORG_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caisse")
                .hasMessageContaining("même type");
        verify(empruntRepository, never()).save(any());
    }

    @Test
    void accorder_autorise_si_emprunt_en_cours_autre_type() {
        stubMembreEtRegles();
        when(empruntRepository.existsByMembreIdAndOrganisationIdAndStatutAndTypeEmprunt(
                        eq(MEMBRE_ID), eq(ORG_ID), eq(StatutEmprunt.EN_COURS), any(TypeEmprunt.class)))
                .thenAnswer(inv -> TypeEmprunt.ETALE.equals(inv.getArgument(3)));

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.SOLIDARITE)).thenReturn(solidarite);
        when(compteService.getCompteOrg(ORG_ID, TypeCompte.CAISSE)).thenReturn(caisse);
        when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.SOLIDARITE)))
                .thenReturn(Compte.builder().id(20L).membreId(MEMBRE_ID).solde(BigDecimal.ZERO).build());
        when(empruntRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(operationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccorderEmpruntRequest req = request(TypeEmprunt.SOLIDARITE, new BigDecimal("10000"));
        accorderEmpruntService.accorder(ORG_ID, req);

        verify(empruntRepository).save(any());
    }

    @Test

    void accorder_caisse_refuse_si_solde_caisse_insuffisant() {

        stubMembreEtRegles();

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.CAISSE)).thenReturn(caisse);



        AccorderEmpruntRequest req = request(TypeEmprunt.CAISSE, new BigDecimal("100000"));



        assertThatThrownBy(() -> accorderEmpruntService.accorder(ORG_ID, req))

                .isInstanceOf(BusinessException.class)

                .hasMessageContaining("Caisse");

        verify(compteService, never()).appliquerMouvement(eq(10L), eq(SensMouvement.DEBIT), any(), eq(false));

    }



    @Test

    void accorder_solidarite_avance_caisse_si_solde_insuffisant() {

        stubMembreEtRegles();

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.SOLIDARITE)).thenReturn(solidarite);

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.CAISSE)).thenReturn(caisse);

        when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.SOLIDARITE)))

                .thenReturn(Compte.builder().id(20L).membreId(MEMBRE_ID).solde(BigDecimal.ZERO).build());

        when(empruntRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(operationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));



        AccorderEmpruntRequest req = request(TypeEmprunt.SOLIDARITE, new BigDecimal("10000"));

        accorderEmpruntService.accorder(ORG_ID, req);



        verify(compteService).appliquerMouvement(eq(10L), eq(SensMouvement.DEBIT), eq(new BigDecimal("5000")), eq(false));

        verify(compteService).appliquerMouvement(eq(11L), eq(SensMouvement.DEBIT), eq(new BigDecimal("5000")), eq(true));

        verify(compteService, never()).appliquerMouvement(eq(11L), eq(SensMouvement.CREDIT), any(), anyBoolean());

        verify(compteService).appliquerMouvement(eq(20L), eq(SensMouvement.DEBIT), eq(new BigDecimal("10000")), eq(true));

        verify(compteService, never()).appliquerMouvement(eq(20L), eq(SensMouvement.CREDIT), any(), anyBoolean());

    }



    @Test

    void accorder_solidarite_sans_avance_si_solde_suffisant() {

        stubMembreEtRegles();

        solidarite.setSolde(new BigDecimal("20000"));

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.SOLIDARITE)).thenReturn(solidarite);

        when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.SOLIDARITE)))

                .thenReturn(Compte.builder().id(20L).membreId(MEMBRE_ID).solde(BigDecimal.ZERO).build());

        when(empruntRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(operationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));



        AccorderEmpruntRequest req = request(TypeEmprunt.SOLIDARITE, new BigDecimal("10000"));

        accorderEmpruntService.accorder(ORG_ID, req);



        verify(compteService, never()).appliquerMouvement(eq(10L), any(), any(), anyBoolean());

        verify(compteService).appliquerMouvement(eq(11L), eq(SensMouvement.DEBIT), eq(new BigDecimal("10000")), eq(true));

        verify(compteService).appliquerMouvement(eq(20L), eq(SensMouvement.DEBIT), eq(new BigDecimal("10000")), eq(true));

        verify(compteService, never()).appliquerMouvement(eq(20L), eq(SensMouvement.CREDIT), any(), anyBoolean());

    }



    @Test

    void accorder_solidarite_refuse_si_caisse_insuffisante_pour_avance() {

        stubMembreEtRegles();

        solidarite.setSolde(BigDecimal.ZERO);

        caisse.setSolde(new BigDecimal("1000"));

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.SOLIDARITE)).thenReturn(solidarite);

        when(compteService.getCompteOrg(ORG_ID, TypeCompte.CAISSE)).thenReturn(caisse);

        when(compteService.getCompteMembre(eq(MEMBRE_ID), eq(TypeCompte.SOLIDARITE)))

                .thenReturn(Compte.builder().id(20L).membreId(MEMBRE_ID).solde(BigDecimal.ZERO).build());



        AccorderEmpruntRequest req = request(TypeEmprunt.SOLIDARITE, new BigDecimal("10000"));



        assertThatThrownBy(() -> accorderEmpruntService.accorder(ORG_ID, req))

                .isInstanceOf(BusinessException.class)

                .hasMessageContaining("Caisse");

    }



    private void stubMembreEtRegles() {

        lenient().when(exerciceService.requireExerciceCourantId(ORG_ID)).thenReturn(1L);

        when(membreRepository.findByIdAndOrganisationId(MEMBRE_ID, ORG_ID))

                .thenReturn(Optional.of(Membre.builder().id(MEMBRE_ID).organisationId(ORG_ID).build()));

        lenient()
                .when(empruntRepository.existsByMembreIdAndOrganisationIdAndStatutAndTypeEmprunt(
                        eq(MEMBRE_ID), eq(ORG_ID), eq(StatutEmprunt.EN_COURS), any(TypeEmprunt.class)))
                .thenReturn(false);

        RegleOperation regleCaisse = regle("Emprunt Caisse", new BigDecimal("5000"));

        RegleOperation regleSol = regle("Emprunt Solidarité", BigDecimal.ZERO);

        when(regleOperationRepository.findByOrganisationId(ORG_ID))

                .thenReturn(List.of(regleCaisse, regleSol));

    }



    private RegleOperation regle(String libelle, BigDecimal fraisFixe) {

        return RegleOperation.builder()

                .id(1L)

                .organisationId(ORG_ID)

                .typeOperation(TypeOperation.EMPRUNT)

                .libelle(libelle)

                .montantMin(new BigDecimal("1000"))

                .montantMax(new BigDecimal("500000"))

                .typeFrais(TypeModeCalcul.FIXE)

                .montantFrais(fraisFixe)

                .nbEcheancesMin(1)

                .nbEcheancesMax(6)

                .nbEcheancesDefaut(1)

                .actif(true)

                .build();

    }



    private AccorderEmpruntRequest request(TypeEmprunt type, BigDecimal montant) {

        AccorderEmpruntRequest req = new AccorderEmpruntRequest();

        req.setMembreId(MEMBRE_ID);

        req.setTypeEmprunt(type);

        req.setMontant(montant);

        req.setDateOctroi(LocalDate.now());

        return req;

    }

}


