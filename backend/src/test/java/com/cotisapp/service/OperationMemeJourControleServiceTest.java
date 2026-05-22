package com.cotisapp.service;

import com.cotisapp.domain.entity.DemandeOperationMembre;
import com.cotisapp.domain.enums.DemandeOperationStatut;
import com.cotisapp.domain.enums.DemandeOperationType;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.dto.request.CotisationHebdoRequest;
import com.cotisapp.dto.request.CotisationMoisRequest;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.DemandeOperationMembreRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.OperationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationMemeJourControleServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 21);
    private static final String SEMAINE_W20 = "2026-W20";
    private static final String SEMAINE_W21 = "2026-W21";
    private static final String MOIS_2026_05 = "2026-05";
    private static final String MOIS_2026_06 = "2026-06";

    @Mock private OperationRepository operationRepository;
    @Mock private DemandeOperationMembreRepository demandeRepository;
    @Mock private EmpruntRepository empruntRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks private OperationMemeJourControleService service;

    @Test
    void cotisationHebdo_refuseSiDejaMemeSemaine() {
        when(operationRepository.existsCotisationHebdoMembreAvecMarqueurSemaine(
                        1L, 2L, "[2026-W21]"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.verifierCotisationHebdo(1L, 2L, hebdo(SEMAINE_W21, DATE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("semaine 2026-W21");
    }

    @Test
    void cotisationHebdo_okSiDemandeHebdoAutreSemaineMemeDate() {
        when(operationRepository.existsCotisationHebdoMembreAvecMarqueurSemaine(anyLong(), anyLong(), anyString()))
                .thenReturn(false);
        when(operationRepository.existsCotisationHebdoMembreEntreDates(anyLong(), anyLong(), any(), any()))
                .thenReturn(false);
        when(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                        1L, 2L, DemandeOperationStatut.EN_ATTENTE))
                .thenReturn(List.of(demandeHebdoEnAttente(SEMAINE_W20, DATE)));

        assertThatCode(() -> service.verifierCotisationHebdo(1L, 2L, hebdo(SEMAINE_W21, DATE)))
                .doesNotThrowAnyException();
    }

    @Test
    void cotisationHebdo_refuseSiDemandeMemeSemaine() {
        when(operationRepository.existsCotisationHebdoMembreAvecMarqueurSemaine(anyLong(), anyLong(), anyString()))
                .thenReturn(false);
        when(operationRepository.existsCotisationHebdoMembreEntreDates(anyLong(), anyLong(), any(), any()))
                .thenReturn(false);
        when(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                        1L, 2L, DemandeOperationStatut.EN_ATTENTE))
                .thenReturn(List.of(demandeHebdoEnAttente(SEMAINE_W21, DATE)));

        assertThatThrownBy(() -> service.verifierCotisationHebdo(1L, 2L, hebdo(SEMAINE_W21, DATE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("semaine 2026-W21")
                .hasMessageContaining("en attente");
    }

    @Test
    void cotisationHebdo_okSiCotisationMoisMemeJour() {
        when(operationRepository.existsCotisationHebdoMembreAvecMarqueurSemaine(anyLong(), anyLong(), anyString()))
                .thenReturn(false);
        when(operationRepository.existsCotisationHebdoMembreEntreDates(anyLong(), anyLong(), any(), any()))
                .thenReturn(false);
        when(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                        1L, 2L, DemandeOperationStatut.EN_ATTENTE))
                .thenReturn(List.of(demandeMoisEnAttente(MOIS_2026_05, DATE)));

        assertThatCode(() -> service.verifierCotisationHebdo(1L, 2L, hebdo(SEMAINE_W21, DATE)))
                .doesNotThrowAnyException();
    }

    @Test
    void cotisationMois_okSiDemandeHebdoMemeJour() {
        when(operationRepository.existsCotisationMoisMembrePourMois(1L, 2L, MOIS_2026_05))
                .thenReturn(false);
        when(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                        1L, 2L, DemandeOperationStatut.EN_ATTENTE))
                .thenReturn(List.of(demandeHebdoEnAttente(SEMAINE_W21, DATE)));

        assertThatCode(() -> service.verifierCotisationMois(1L, 2L, mois(MOIS_2026_05, DATE)))
                .doesNotThrowAnyException();
    }

    @Test
    void cotisationMois_refuseSiDemandeMoisEnAttente() {
        when(operationRepository.existsCotisationMoisMembrePourMois(1L, 2L, MOIS_2026_05))
                .thenReturn(false);
        when(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                        1L, 2L, DemandeOperationStatut.EN_ATTENTE))
                .thenReturn(List.of(demandeMoisEnAttente(MOIS_2026_05, DATE)));

        assertThatThrownBy(() -> service.verifierCotisationMois(1L, 2L, mois(MOIS_2026_05, DATE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mois 2026-05")
                .hasMessageContaining("en attente");
    }

    @Test
    void cotisationMois_okSiDemandeMoisAutrePeriodeMemeDate() {
        when(operationRepository.existsCotisationMoisMembrePourMois(1L, 2L, MOIS_2026_06))
                .thenReturn(false);
        when(demandeRepository.findByOrganisationIdAndMembreIdAndStatutOrderByDateDemandeDesc(
                        1L, 2L, DemandeOperationStatut.EN_ATTENTE))
                .thenReturn(List.of(demandeMoisEnAttente(MOIS_2026_05, DATE)));

        assertThatCode(() -> service.verifierCotisationMois(1L, 2L, mois(MOIS_2026_06, DATE)))
                .doesNotThrowAnyException();
    }

    @Test
    void remboursement_sansControleUniciteParJour() {
        assertThatCode(() -> service.verifierRemboursement(1L, 2L, TypeEmprunt.ETALE, DATE))
                .doesNotThrowAnyException();
    }

    @Test
    void octroiEmprunt_caisse_okSiOctroiSolidariteMemeJour() {
        when(operationRepository.existsOctroiEmpruntMemeTypeMemeJour(1L, 2L, TypeEmprunt.CAISSE, DATE))
                .thenReturn(false);

        assertThatCode(() -> service.verifierOctroiEmprunt(1L, 2L, TypeEmprunt.CAISSE, DATE))
                .doesNotThrowAnyException();
    }

    private static CotisationHebdoRequest hebdo(String semaineKey, LocalDate date) {
        CotisationHebdoRequest r = new CotisationHebdoRequest();
        r.setMembreId(2L);
        r.setSemaineKey(semaineKey);
        r.setMontant(new BigDecimal("5000"));
        r.setDateOperation(date);
        return r;
    }

    private static CotisationMoisRequest mois(String moisAnnee, LocalDate date) {
        CotisationMoisRequest r = new CotisationMoisRequest();
        r.setMembreId(2L);
        r.setMoisAnnee(moisAnnee);
        r.setMontant(new BigDecimal("5000"));
        r.setDateOperation(date);
        return r;
    }

    private DemandeOperationMembre demandeHebdoEnAttente(String semaineKey, LocalDate date) {
        String payload = String.format(
                """
                {"membreId":2,"semaineKey":"%s","montant":5000,"dateOperation":"%s"}\
                """,
                semaineKey,
                date);
        return DemandeOperationMembre.builder()
                .id(10L)
                .organisationId(1L)
                .membreId(2L)
                .demandeurUtilisateurId(99L)
                .typeDemande(DemandeOperationType.COTISATION_HEBDO)
                .statut(DemandeOperationStatut.EN_ATTENTE)
                .payloadJson(payload)
                .montant(new BigDecimal("5000"))
                .dateDemande(LocalDateTime.now())
                .build();
    }

    private DemandeOperationMembre demandeMoisEnAttente(String moisAnnee, LocalDate date) {
        String payload = String.format(
                """
                {"membreId":2,"moisAnnee":"%s","montant":5000,"dateOperation":"%s"}\
                """,
                moisAnnee,
                date);
        return DemandeOperationMembre.builder()
                .id(11L)
                .organisationId(1L)
                .membreId(2L)
                .demandeurUtilisateurId(99L)
                .typeDemande(DemandeOperationType.COTISATION_MOIS)
                .statut(DemandeOperationStatut.EN_ATTENTE)
                .payloadJson(payload)
                .montant(new BigDecimal("5000"))
                .dateDemande(LocalDateTime.now())
                .build();
    }
}

