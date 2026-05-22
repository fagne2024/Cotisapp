package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Echeance;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.StatutEcheance;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.dto.response.CotisationMoisStatResponse;
import com.cotisapp.dto.response.DashboardResponse;
import com.cotisapp.dto.response.MembreResponse;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int OPERATIONS_RECENTES_MAX = 8;
    private static final Comparator<PosteMembre> POSTE_ORDER = Comparator.comparingInt(p -> switch (p) {
        case PRESIDENT -> 0;
        case VICE_PRESIDENT -> 1;
        case SECRETAIRE_GENERAL -> 2;
        case SECRETAIRE_GENERAL_ADJOINT -> 3;
        case TRESORIER -> 4;
        case SUPERVISEUR -> 5;
        default -> 99;
    });

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final CompteRepository compteRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final MembreService membreService;
    private final OperationMapperService operationMapperService;
    private final ExerciceService exerciceService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);

        List<Membre> membresActifs = membreRepository.findByOrganisationIdAndActifTrue(orgId);
        long bureau = membresActifs.stream().filter(m -> m.getPoste() != PosteMembre.SIMPLE).count();
        long simples = membresActifs.stream().filter(m -> m.getPoste() == PosteMembre.SIMPLE).count();

        List<Emprunt> emprunts = empruntRepository.findByOrganisationIdAndStatut(orgId, StatutEmprunt.EN_COURS);
        LocalDate today = LocalDate.now();
        long enCours = emprunts.stream().filter(e -> e.getStatut() == StatutEmprunt.EN_COURS).count();
        long enRetard = emprunts.stream()
                .filter(e -> e.getStatut() == StatutEmprunt.EN_COURS)
                .filter(e -> empruntEnRetard(e, today))
                .count();

        List<MembreResponse> bureauListe = membresActifs.stream()
                .filter(m -> m.getPoste() != PosteMembre.SIMPLE)
                .sorted(Comparator.comparing(Membre::getPoste, POSTE_ORDER)
                        .thenComparing(m -> m.getNomComplet(), String.CASE_INSENSITIVE_ORDER))
                .map(membreService::toResponse)
                .toList();

        List<OperationResponse> operations = operationRepository
                .findByOrganisationIdAndExerciceIdOrderByDateCreationDesc(orgId, exerciceId)
                .stream()
                .limit(OPERATIONS_RECENTES_MAX)
                .map(operationMapperService::toResponse)
                .toList();

        int annee = LocalDate.now().getYear();
        List<CotisationMoisStatResponse> evolutionCotisations =
                buildEvolutionCotisations(orgId, exerciceId, annee);

        return DashboardResponse.builder()
                .soldeCaisse(soldeOrg(orgId, TypeCompte.CAISSE))
                .soldeSolidarite(soldeOrg(orgId, TypeCompte.SOLIDARITE))
                .soldeBanque(soldeOrg(orgId, TypeCompte.BANQUE))
                .nbMembresActifs(membresActifs.size())
                .nbMembresBureau(bureau)
                .nbMembresSimples(simples)
                .nbEmpruntsEnCours(enCours)
                .nbEmpruntsEnRetard(enRetard)
                .operationsRecentes(operations)
                .bureau(bureauListe)
                .evolutionCotisations(evolutionCotisations)
                .evolutionAnnee(annee)
                .build();
    }

    private List<CotisationMoisStatResponse> buildEvolutionCotisations(Long orgId, Long exerciceId, int annee) {
        Map<Integer, BigDecimal> parMois = new HashMap<>();
        for (Object[] row : operationRepository.sumCotisationsParMoisAnnee(orgId, exerciceId, annee)) {
            int mois = ((Number) row[0]).intValue();
            BigDecimal montant = row[1] instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            parMois.put(mois, montant);
        }

        BigDecimal somme = parMois.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long nbMoisAvecDonnees = parMois.values().stream().filter(m -> m.compareTo(BigDecimal.ZERO) > 0).count();
        BigDecimal objectifMensuel = BigDecimal.ZERO;
        if (nbMoisAvecDonnees > 0) {
            objectifMensuel = somme.divide(BigDecimal.valueOf(nbMoisAvecDonnees), 0, RoundingMode.HALF_UP);
        }

        List<CotisationMoisStatResponse> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            BigDecimal cotis = parMois.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal objectif = objectifMensuel;
            if (objectif.compareTo(BigDecimal.ZERO) == 0 && cotis.compareTo(BigDecimal.ZERO) > 0) {
                objectif = cotis;
            }
            result.add(CotisationMoisStatResponse.builder()
                    .mois(m)
                    .montantCotisations(cotis)
                    .objectif(objectif)
                    .build());
        }
        return result;
    }

    private BigDecimal soldeOrg(Long orgId, TypeCompte type) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(orgId, type, ProprietaireCompte.ORGANISATION)
                .map(Compte::getSolde)
                .orElse(BigDecimal.ZERO);
    }

    private boolean empruntEnRetard(Emprunt emprunt, LocalDate today) {
        if (emprunt.getEcheances() == null) {
            return false;
        }
        for (Echeance ech : emprunt.getEcheances()) {
            if (ech.getStatut() == StatutEcheance.PAYE) {
                continue;
            }
            if (ech.getDateEcheance().isBefore(today)) {
                return true;
            }
        }
        return false;
    }
}
