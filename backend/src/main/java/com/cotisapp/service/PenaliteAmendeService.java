package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.entity.RegleOperation;
import com.cotisapp.domain.enums.FamilleCompte;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.AppliquerSanctionRequest;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.dto.response.PenaliteAmendeHistoriqueLigneResponse;
import com.cotisapp.dto.response.PenaliteAmendePanneauResponse;
import com.cotisapp.dto.response.PenaliteAmendeStatsMoisResponse;
import com.cotisapp.dto.response.PenaliteAmendeTopMembreResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PenaliteAmendeService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter MOIS_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final MembreRepository membreRepository;
    private final OperationRepository operationRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final CompteRepository compteRepository;
    private final CompteService compteService;
    private final ParametrageCompteService parametrageCompteService;
    private final ExerciceService exerciceService;
    private final OperationPlanadGuardService operationPlanadGuardService;
    private final JournalService journalService;
    private final OperationMapperService operationMapperService;

    @Transactional(readOnly = true)
    public PenaliteAmendePanneauResponse panneau(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));
        BigDecimal soldeCaisse = soldeCaisseOrg(orgId);
        List<PenaliteAmendeHistoriqueLigneResponse> historique = historique(orgId);
        return PenaliteAmendePanneauResponse.builder()
                .soldeCaisse(soldeCaisse)
                .statsMois(statsMoisCourant(historique))
                .historique(historique)
                .topPenalises(topPenalises(orgId, historique))
                .build();
    }

    @Transactional
    public OperationResponse appliquer(Long orgId, AppliquerSanctionRequest request) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));
        Membre membre = membreRepository.findByIdAndOrganisationId(request.getMembreId(), orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));

        TypeOperation typeOp = resoudreType(request.getType());
        FamilleCompte famille = typeOp == TypeOperation.PENALITE ? FamilleCompte.PENALITE : FamilleCompte.AMENDE;
        if (!parametrageCompteService.familleActive(orgId, famille)) {
            throw new BusinessException(
                    "Le compte " + (typeOp == TypeOperation.PENALITE ? "pénalité" : "amende")
                            + " n'est pas activé — activez-le dans le paramétrage des comptes");
        }

        RegleOperation regle = regleOperationRepository
                .findByOrganisationIdAndTypeOperationAndActifTrue(orgId, typeOp)
                .orElseThrow(() -> new BusinessException("Règle " + typeOp + " introuvable ou inactive"));

        BigDecimal montant = request.getMontant();
        if (regle.getMontantMin() != null && montant.compareTo(regle.getMontantMin()) < 0) {
            throw new BusinessException("Montant inférieur au minimum: " + regle.getMontantMin());
        }
        if (regle.getMontantMax() != null && montant.compareTo(regle.getMontantMax()) > 0) {
            throw new BusinessException("Montant supérieur au maximum: " + regle.getMontantMax());
        }

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, exerciceId, request.getDateOperation());

        String observation = construireObservation(request.getMotif(), request.getObservation());
        Operation operation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .membreId(membre.getId())
                .typeOperation(typeOp)
                .montant(montant)
                .dateOperation(request.getDateOperation())
                .observation(observation)
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        List<MouvementCompte> mouvements = new ArrayList<>();
        TypeCompte compteMembreType = typeOp == TypeOperation.PENALITE ? TypeCompte.PENALITE : TypeCompte.AMENDE;
        Compte compteMembre = compteService.creerCompteMembre(orgId, membre.getId(), compteMembreType, null);
        Compte caisseOrg = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);

        ajouterMouvement(operation, mouvements, compteMembre.getId(), SensMouvement.CREDIT, montant, true);
        ajouterMouvement(operation, mouvements, caisseOrg.getId(), SensMouvement.CREDIT, montant, false);

        operation.setMouvements(mouvements);
        Operation saved = operationRepository.save(operation);
        journalService.enregistrer(orgId, typeOp.name(), "Opération " + saved.getId());
        return operationMapperService.toResponse(saved);
    }

    private List<PenaliteAmendeHistoriqueLigneResponse> historique(Long orgId) {
        List<Operation> operations = operationRepository
                .findByOrganisationIdAndTypeOperationInAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.PENALITE, TypeOperation.AMENDE));
        if (operations.isEmpty()) {
            return List.of();
        }

        Set<Long> membreIds = operations.stream()
                .map(Operation::getMembreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Membre> membres = membreRepository.findAllById(membreIds).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));

        List<PenaliteAmendeHistoriqueLigneResponse> lignes = new ArrayList<>();
        for (Operation op : operations) {
            Membre membre = op.getMembreId() != null ? membres.get(op.getMembreId()) : null;
            TypeOperation type = op.getTypeOperation();
            lignes.add(PenaliteAmendeHistoriqueLigneResponse.builder()
                    .operationId(op.getId())
                    .membreId(membre != null ? membre.getId() : null)
                    .membreNom(membre != null ? membre.getNomComplet() : "—")
                    .codeMembre(membre != null ? membre.getCodeMembre() : "")
                    .type(type == TypeOperation.PENALITE ? "pen" : "am")
                    .motif(extraireMotif(op.getObservation()))
                    .montant(nz(op.getMontant()))
                    .dateOperation(op.getDateOperation())
                    .dateLabel(op.getDateOperation().format(DATE_LABEL))
                    .annulee(Boolean.TRUE.equals(op.getAnnulee()))
                    .build());
        }
        return lignes;
    }

    private PenaliteAmendeStatsMoisResponse statsMoisCourant(List<PenaliteAmendeHistoriqueLigneResponse> historique) {
        YearMonth mois = YearMonth.now();
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        int penalites = 0;
        int amendes = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (PenaliteAmendeHistoriqueLigneResponse h : historique) {
            if (h.isAnnulee() || h.getDateOperation() == null) {
                continue;
            }
            if (h.getDateOperation().isBefore(debut) || h.getDateOperation().isAfter(fin)) {
                continue;
            }
            if ("pen".equals(h.getType())) {
                penalites++;
            } else {
                amendes++;
            }
            total = total.add(nz(h.getMontant()));
        }
        String label = MOIS_LABEL.format(mois.atDay(1));
        if (!label.isEmpty()) {
            label = label.substring(0, 1).toUpperCase(Locale.FRENCH) + label.substring(1);
        }
        return PenaliteAmendeStatsMoisResponse.builder()
                .moisLabel(label)
                .penalites(penalites)
                .amendes(amendes)
                .totalEncaisse(total)
                .build();
    }

    private List<PenaliteAmendeTopMembreResponse> topPenalises(
            Long orgId, List<PenaliteAmendeHistoriqueLigneResponse> historique) {
        Map<Long, Membre> membres = membreRepository.findByOrganisationId(orgId).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));

        Map<Long, BigDecimal> totaux = new HashMap<>();
        Map<Long, Integer> nbPen = new HashMap<>();
        Map<Long, Integer> nbAm = new HashMap<>();

        for (PenaliteAmendeHistoriqueLigneResponse h : historique) {
            if (h.isAnnulee() || h.getMembreId() == null) {
                continue;
            }
            Long mid = h.getMembreId();
            totaux.merge(mid, nz(h.getMontant()), BigDecimal::add);
            if ("pen".equals(h.getType())) {
                nbPen.merge(mid, 1, Integer::sum);
            } else {
                nbAm.merge(mid, 1, Integer::sum);
            }
        }

        return totaux.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Membre m = membres.get(e.getKey());
                    if (m == null) {
                        return null;
                    }
                    int p = nbPen.getOrDefault(e.getKey(), 0);
                    int a = nbAm.getOrDefault(e.getKey(), 0);
                    String detail = detailTop(p, a, m.getCodeMembre());
                    return PenaliteAmendeTopMembreResponse.builder()
                            .membreId(m.getId())
                            .nom(m.getNomComplet())
                            .codeMembre(m.getCodeMembre())
                            .detail(detail)
                            .total(e.getValue())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static String detailTop(int penalites, int amendes, String code) {
        List<String> parts = new ArrayList<>();
        if (penalites > 0) {
            parts.add(penalites + " pénalité" + (penalites > 1 ? "s" : ""));
        }
        if (amendes > 0) {
            parts.add(amendes + " amende" + (amendes > 1 ? "s" : ""));
        }
        String base = parts.isEmpty() ? "Sanctions" : String.join(" · ", parts);
        return base + " · " + code;
    }

    private BigDecimal soldeCaisseOrg(Long orgId) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(
                        orgId, TypeCompte.CAISSE, ProprietaireCompte.ORGANISATION)
                .map(c -> nz(c.getSolde()))
                .orElse(BigDecimal.ZERO);
    }

    private void ajouterMouvement(
            Operation operation,
            List<MouvementCompte> mouvements,
            Long compteId,
            SensMouvement sens,
            BigDecimal montant,
            boolean autoriserNegatifMembre) {
        MouvementCompte mc = MouvementCompte.builder()
                .operation(operation)
                .compteId(compteId)
                .sens(sens)
                .montant(montant)
                .build();
        operation.getMouvements().add(mc);
        mouvements.add(mc);
        compteService.appliquerMouvement(compteId, sens, montant, autoriserNegatifMembre);
    }

    private static TypeOperation resoudreType(String type) {
        if (type == null) {
            throw new BusinessException("Type de sanction requis (PENALITE ou AMENDE)");
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "PEN", "PENALITE" -> TypeOperation.PENALITE;
            case "AM", "AMENDE" -> TypeOperation.AMENDE;
            default -> throw new BusinessException("Type invalide: " + type);
        };
    }

    static String construireObservation(String motif, String observation) {
        String m = motif != null ? motif.trim() : "";
        if (m.isEmpty()) {
            throw new BusinessException("Le motif est requis");
        }
        String prefix = "[motif: " + m + "]";
        if (observation == null || observation.isBlank()) {
            return prefix;
        }
        return prefix + " " + observation.trim();
    }

    static String extraireMotif(String observation) {
        if (observation == null || observation.isBlank()) {
            return "—";
        }
        String obs = observation.trim();
        if (!obs.startsWith("[motif:")) {
            return obs.length() > 80 ? obs.substring(0, 77) + "…" : obs;
        }
        int fin = obs.indexOf(']');
        if (fin > 8) {
            return obs.substring(8, fin).trim();
        }
        return obs;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
