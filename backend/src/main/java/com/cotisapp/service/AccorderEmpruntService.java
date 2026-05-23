package com.cotisapp.service;

import com.cotisapp.domain.entity.*;
import com.cotisapp.domain.enums.*;
import com.cotisapp.dto.request.AccorderEmpruntRequest;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.RegleOperationRepository;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccorderEmpruntService {

    private final MembreRepository membreRepository;
    private final RegleOperationRepository regleOperationRepository;
    private final EmpruntRepository empruntRepository;
    private final OperationRepository operationRepository;
    private final CompteService compteService;
    private final JournalService journalService;
    private final EmpruntService empruntService;
    private final ExerciceService exerciceService;
    private final OperationPlanadGuardService operationPlanadGuardService;
    private final OperationMemeJourControleService operationMemeJourControleService;

    @Transactional
    public com.cotisapp.dto.response.EmpruntResponse accorder(Long orgId, AccorderEmpruntRequest request) {
        Membre membre = membreRepository.findByIdAndOrganisationId(request.getMembreId(), orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));

        verifierPasDEmpruntEnCoursMemeType(orgId, request.getMembreId(), request.getTypeEmprunt());
        operationMemeJourControleService.verifierOctroiEmprunt(
                orgId, request.getMembreId(), request.getTypeEmprunt(), request.getDateOctroi());

        RegleOperation regle = trouverRegleEmprunt(orgId, request.getTypeEmprunt());
        if (!Boolean.TRUE.equals(regle.getActif())) {
            throw new BusinessException("Règle d'emprunt inactive: " + regle.getLibelle());
        }

        EmpruntCalculHelper.validerMontant(request.getMontant(), regle);
        EmpruntCalculHelper.SimulationEmprunt sim = EmpruntCalculHelper.simuler(
                request.getMontant(), regle, request.getNbEcheances());
        EmpruntCalculHelper.validerMontantEcheance(sim, regle);

        BigDecimal capital = sim.capital();
        BigDecimal frais = sim.frais();
        BigDecimal debitOrg = capital.add(frais);

        TypeCompte compteOrg = compteOrgPourType(request.getTypeEmprunt());
        TypeCompte compteMembre = compteMembrePourType(request.getTypeEmprunt());

        compteService.ensureComptesMembre(orgId, request.getMembreId());
        Compte membreCompte = compteService.getCompteMembre(request.getMembreId(), compteMembre);
        Compte orgCompte = compteService.getCompteOrg(orgId, compteOrg);

        if (request.getTypeEmprunt() != TypeEmprunt.SOLIDARITE) {
            verifierSoldeSuffisant(orgCompte, debitOrg, libelleCompteOrg(compteOrg));
        }

        BigDecimal avanceCaisse = BigDecimal.ZERO;
        if (request.getTypeEmprunt() == TypeEmprunt.SOLIDARITE) {
            avanceCaisse = calculerAvanceCaisseVersSolidarite(orgCompte.getSolde(), debitOrg);
            if (avanceCaisse.compareTo(BigDecimal.ZERO) > 0) {
                Compte compteCaisse = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
                verifierSoldeSuffisant(compteCaisse, avanceCaisse, libelleCompteOrg(TypeCompte.CAISSE));
            }
        }

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, exerciceId, request.getDateOctroi());
        Emprunt emprunt = Emprunt.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .membreId(request.getMembreId())
                .typeEmprunt(request.getTypeEmprunt())
                .montantTotal(sim.totalRembourser())
                .montantRembourse(BigDecimal.ZERO)
                .montantFrais(frais)
                .montantAvanceCaisse(avanceCaisse)
                .dateCreation(request.getDateOctroi())
                .observation(request.getObservation())
                .statut(StatutEmprunt.EN_COURS)
                .build();

        Integer jourEcheance = regle.getJourEcheanceMois();
        if (request.getTypeEmprunt() == TypeEmprunt.ETALE) {
            genererEcheances(emprunt, sim, request.getDateOctroi(), jourEcheance);
        } else if (request.getTypeEmprunt() == TypeEmprunt.CAISSE && sim.nbEcheances() > 0) {
            genererEcheancesCaisse(emprunt, sim, request.getDateOctroi(), jourEcheance);
        } else if (request.getTypeEmprunt() == TypeEmprunt.SOLIDARITE && sim.nbEcheances() > 0) {
            genererEcheances(emprunt, sim, request.getDateOctroi(), jourEcheance);
        }

        emprunt = empruntRepository.save(emprunt);

        String observationOperation = request.getObservation();
        if (avanceCaisse.compareTo(BigDecimal.ZERO) > 0) {
            observationOperation = EmpruntAvanceCaisseHelper.fusionnerObservation(
                    observationOperation, EmpruntAvanceCaisseHelper.observationOctroiAvance(avanceCaisse));
        }

        Operation operation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .membreId(request.getMembreId())
                .typeOperation(TypeOperation.EMPRUNT)
                .montant(capital)
                .montantFrais(frais)
                .dateOperation(request.getDateOctroi())
                .empruntId(emprunt.getId())
                .observation(observationOperation)
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        List<MouvementCompte> mouvements = new ArrayList<>();
        if (request.getTypeEmprunt() == TypeEmprunt.SOLIDARITE) {
            appliquerMouvementsEmpruntSolidarite(orgId, operation, mouvements, orgCompte, debitOrg, avanceCaisse);
        } else {
            mouvements.add(ajouterMouvement(operation, orgCompte.getId(), SensMouvement.DEBIT, debitOrg));
            compteService.appliquerMouvement(orgCompte.getId(), SensMouvement.DEBIT, debitOrg, false);
        }

        mouvements.add(ajouterMouvement(operation, membreCompte.getId(), SensMouvement.DEBIT, capital));
        compteService.appliquerMouvement(membreCompte.getId(), SensMouvement.DEBIT, capital, true);

        if (frais.compareTo(BigDecimal.ZERO) > 0) {
            mouvements.add(ajouterMouvement(operation, membreCompte.getId(), SensMouvement.DEBIT, frais));
            compteService.appliquerMouvement(membreCompte.getId(), SensMouvement.DEBIT, frais, true);
            Compte caisseOrg = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
            mouvements.add(ajouterMouvement(operation, caisseOrg.getId(), SensMouvement.CREDIT, frais));
            compteService.appliquerMouvement(caisseOrg.getId(), SensMouvement.CREDIT, frais, true);
        }

        operation.setMouvements(mouvements);
        Operation savedOp = operationRepository.save(operation);
        journalService.enregistrer(
                orgId,
                "EMPRUNT",
                "Octroi emprunt "
                        + request.getTypeEmprunt().name()
                        + " — "
                        + JournalModificationFormatter.cibleMembre(
                                membre.getCodeMembre(), membre.getPrenom(), membre.getNom(), membre.getId())
                        + " — capital "
                        + JournalModificationFormatter.montantFcfa(capital)
                        + (frais.compareTo(BigDecimal.ZERO) > 0
                                ? ", frais " + JournalModificationFormatter.montantFcfa(frais)
                                : "")
                        + " (réf. emprunt n°"
                        + emprunt.getId()
                        + ", opération n°"
                        + savedOp.getId()
                        + ")");

        return empruntService.getById(orgId, emprunt.getId());
    }

    /**
     * Décaissement solidarité : débit Caisse = avance (restituée en priorité au remboursement),
     * débit Solidarité = part propre du fonds (même montant recrédité au remboursement).
     */
    private void appliquerMouvementsEmpruntSolidarite(
            Long orgId,
            Operation operation,
            List<MouvementCompte> mouvements,
            Compte compteSolidarite,
            BigDecimal debitTotal,
            BigDecimal avanceCaisse) {
        if (avanceCaisse.compareTo(BigDecimal.ZERO) > 0) {
            Compte compteCaisse = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
            verifierSoldeSuffisant(compteCaisse, avanceCaisse, libelleCompteOrg(TypeCompte.CAISSE));
            mouvements.add(ajouterMouvement(operation, compteCaisse.getId(), SensMouvement.DEBIT, avanceCaisse));
            compteService.appliquerMouvement(compteCaisse.getId(), SensMouvement.DEBIT, avanceCaisse, false);
        }
        BigDecimal debitSolidarite = EmpruntAvanceCaisseHelper.debitSolidaritePropre(debitTotal, avanceCaisse);
        if (debitSolidarite.compareTo(BigDecimal.ZERO) > 0) {
            mouvements.add(ajouterMouvement(operation, compteSolidarite.getId(), SensMouvement.DEBIT, debitSolidarite));
            compteService.appliquerMouvement(compteSolidarite.getId(), SensMouvement.DEBIT, debitSolidarite, true);
        }
    }

    static BigDecimal calculerAvanceCaisseVersSolidarite(BigDecimal soldeSolidarite, BigDecimal debitTotal) {
        BigDecimal disponible = soldeSolidarite.max(BigDecimal.ZERO);
        return debitTotal.subtract(disponible).max(BigDecimal.ZERO);
    }

    private void genererEcheances(
            Emprunt emprunt,
            EmpruntCalculHelper.SimulationEmprunt sim,
            LocalDate dateDebut,
            Integer jourEcheanceMois) {
        List<BigDecimal> montants = sim.montantsEcheances();
        for (int i = 0; i < montants.size(); i++) {
            emprunt.getEcheances().add(Echeance.builder()
                    .emprunt(emprunt)
                    .numero(i + 1)
                    .montantEcheance(montants.get(i))
                    .dateEcheance(EmpruntEcheanceDateHelper.calculerDateEcheance(
                            dateDebut, i + 1, jourEcheanceMois))
                    .build());
        }
    }

    private void genererEcheancesCaisse(
            Emprunt emprunt,
            EmpruntCalculHelper.SimulationEmprunt sim,
            LocalDate dateDebut,
            Integer jourEcheanceMois) {
        genererEcheances(emprunt, sim, dateDebut, jourEcheanceMois);
    }

    private RegleOperation trouverRegleEmprunt(Long orgId, TypeEmprunt type) {
        List<RegleOperation> emprunts = regleOperationRepository.findByOrganisationId(orgId).stream()
                .filter(r -> r.getTypeOperation() == TypeOperation.EMPRUNT)
                .toList();
        return switch (type) {
            case ETALE -> trouverParLibelle(emprunts, "étalé", "etale", "financement")
                    .orElseThrow(() -> new BusinessException("Règle emprunt étalé introuvable"));
            case CAISSE -> trouverParLibelle(emprunts, "caisse")
                    .orElseThrow(() -> new BusinessException("Règle emprunt caisse introuvable"));
            case SOLIDARITE -> trouverParLibelle(emprunts, "solidar")
                    .orElseThrow(() -> new BusinessException("Règle emprunt solidarité introuvable"));
        };
    }

    private java.util.Optional<RegleOperation> trouverParLibelle(List<RegleOperation> regles, String... mots) {
        return regles.stream()
                .filter(RegleOperation::getActif)
                .filter(r -> {
                    String lib = r.getLibelle() != null ? r.getLibelle().toLowerCase() : "";
                    for (String m : mots) {
                        if (lib.contains(m.toLowerCase())) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst();
    }

    private TypeCompte compteOrgPourType(TypeEmprunt type) {
        return switch (type) {
            case SOLIDARITE -> TypeCompte.SOLIDARITE;
            case ETALE, CAISSE -> TypeCompte.CAISSE;
        };
    }

    private TypeCompte compteMembrePourType(TypeEmprunt type) {
        return switch (type) {
            case SOLIDARITE -> TypeCompte.SOLIDARITE;
            case CAISSE -> TypeCompte.EPARGNE_HEBDO;
            case ETALE -> TypeCompte.EPARGNE_MOIS;
        };
    }

    private String libelleCompteOrg(TypeCompte type) {
        return switch (type) {
            case SOLIDARITE -> "Solidarité";
            case CAISSE -> "Caisse";
            default -> type.name();
        };
    }

    /**
     * Un membre peut avoir un emprunt en cours par type (étalé, solidarité, caisse) en parallèle,
     * mais pas deux crédits actifs pour le même type.
     */
    private void verifierPasDEmpruntEnCoursMemeType(Long orgId, Long membreId, TypeEmprunt typeDemande) {
        if (empruntRepository.existsByMembreIdAndOrganisationIdAndStatutAndTypeEmprunt(
                membreId, orgId, StatutEmprunt.EN_COURS, typeDemande)) {
            throw new BusinessException(
                    "Ce membre a déjà un emprunt « "
                            + libelleTypeEmprunt(typeDemande)
                            + " » en cours. Remboursez-le avant d'en octroyer un nouveau du même type.");
        }
    }

    private static String libelleTypeEmprunt(TypeEmprunt type) {
        return switch (type) {
            case ETALE -> "étalé";
            case SOLIDARITE -> "solidarité";
            case CAISSE -> "caisse";
        };
    }

    private void verifierSoldeSuffisant(Compte compte, BigDecimal debit, String libelle) {
        if (compte.getSolde().compareTo(debit) < 0) {
            throw new BusinessException(String.format(
                    "Solde %s insuffisant (disponible: %s, requis: %s). La caisse ne peut pas être négative.",
                    libelle, compte.getSolde(), debit));
        }
    }

    private MouvementCompte ajouterMouvement(Operation op, Long compteId, SensMouvement sens, BigDecimal montant) {
        MouvementCompte mc = MouvementCompte.builder()
                .operation(op)
                .compteId(compteId)
                .sens(sens)
                .montant(montant)
                .build();
        op.getMouvements().add(mc);
        return mc;
    }
}
