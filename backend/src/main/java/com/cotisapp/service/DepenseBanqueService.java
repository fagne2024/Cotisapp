package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.request.BanqueMouvementRequest;
import com.cotisapp.dto.request.DepenseRequest;
import com.cotisapp.dto.response.*;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.domain.entity.ReleveBancaire;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.MouvementCompteRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.ReleveBancaireRepository;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepenseBanqueService {

    private static final Pattern CAT_PATTERN = Pattern.compile("^\\[cat:([a-z_]+)]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BK_TYPE_PATTERN = Pattern.compile("^\\[(vers|ret)]\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, CategorieMeta> CATEGORIES = Map.ofEntries(
            Map.entry("restauration", new CategorieMeta("🍽", "Restauration")),
            Map.entry("transport", new CategorieMeta("🚗", "Transport")),
            Map.entry("fournitures", new CategorieMeta("📦", "Fournitures")),
            Map.entry("loyer", new CategorieMeta("🏠", "Loyer local")),
            Map.entry("energie", new CategorieMeta("💡", "Électricité / Eau")),
            Map.entry("communication", new CategorieMeta("📞", "Communication")),
            Map.entry("sante", new CategorieMeta("🏥", "Santé / Urgence")),
            Map.entry("autre", new CategorieMeta("📝", "Autre"))
    );

    private final OperationRepository operationRepository;
    private final OperationPlanadGuardService operationPlanadGuardService;
    private final MouvementCompteRepository mouvementCompteRepository;
    private final CompteRepository compteRepository;
    private final CompteService compteService;
    private final JournalService journalService;
    private final ReleveBancaireRepository releveBancaireRepository;
    private final ReleveBancaireStorageService releveBancaireStorageService;
    private final ExerciceService exerciceService;
    private final MembreRepository membreRepository;
    private final EmpruntRepository empruntRepository;

    @Transactional(readOnly = true)
    public DepenseBanqueDashboardResponse chargerTableauDeBord(Long orgId) {
        BigDecimal soldeCaisse = soldeOrg(orgId, TypeCompte.CAISSE);
        BigDecimal soldeBanque = soldeOrgOptional(orgId, TypeCompte.BANQUE);

        YearMonth mois = YearMonth.now();
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();

        List<Operation> depenses = operationRepository
                .findByOrganisationIdAndTypeOperationOrderByDateOperationDescDateCreationDesc(
                        orgId, TypeOperation.DEPENSE);
        List<Operation> depensesMois = depenses.stream()
                .filter(o -> !o.getDateOperation().isBefore(debut) && !o.getDateOperation().isAfter(fin))
                .toList();

        BigDecimal totalMois = depensesMois.stream()
                .map(Operation::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DepenseLigneResponse> recentes = depenses.stream()
                .limit(20)
                .map(this::toDepenseLigne)
                .toList();

        List<DepenseParCategorieResponse> parCat = agregerParCategorie(depensesMois);

        List<Operation> banqueOps = operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId,
                        List.of(TypeOperation.BANQUE_VERSEMENT, TypeOperation.BANQUE_RETRAIT),
                        debut.minusYears(2),
                        fin.plusYears(1));
        Map<Long, ReleveBancaire> relevesParOperation = chargerRelevesParOperation(banqueOps);
        List<MouvementBanqueLigneResponse> mouvements =
                construireMouvementsBanque(banqueOps, soldeBanque, relevesParOperation);

        Compte caisse = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
        List<MouvementCompte> mouvementsCaisseRaw = mouvementCompteRepository.findByOrganisationAndCompteBetween(
                orgId, caisse.getId(), debut.minusYears(2), fin.plusYears(1));
        BigDecimal entreesMois = BigDecimal.ZERO;
        BigDecimal sortiesMois = BigDecimal.ZERO;
        for (MouvementCompte mc : mouvementsCaisseRaw) {
            Operation op = mc.getOperation();
            if (op.getDateOperation().isBefore(debut) || op.getDateOperation().isAfter(fin)) {
                continue;
            }
            if (mc.getSens() == SensMouvement.CREDIT) {
                entreesMois = entreesMois.add(mc.getMontant());
            } else {
                sortiesMois = sortiesMois.add(mc.getMontant());
            }
        }
        List<MouvementCaisseLigneResponse> mouvementsCaisse =
                construireMouvementsCaisse(mouvementsCaisseRaw, soldeCaisse);

        return DepenseBanqueDashboardResponse.builder()
                .soldeCaisse(soldeCaisse)
                .soldeBanque(soldeBanque)
                .totalDepensesMois(totalMois)
                .depensesRecentes(recentes)
                .depensesParCategorie(parCat)
                .mouvementsBanque(mouvements)
                .entreesCaisseMois(entreesMois)
                .sortiesCaisseMois(sortiesMois)
                .mouvementsCaisse(mouvementsCaisse)
                .build();
    }

    @Transactional
    public OperationResponse enregistrerDepense(Long orgId, DepenseRequest request) {
        validerCategorie(request.getCategorieId());
        TypeCompte compte = compteDebite(request.getCompteDebite());

        String obs = construireObservationDepense(request);
        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, exerciceId, request.getDateDepense());
        Operation operation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .typeOperation(TypeOperation.DEPENSE)
                .montant(request.getMontant())
                .dateOperation(request.getDateDepense())
                .observation(obs)
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        Compte source = compteService.getCompteOrg(orgId, compte);
        ajouterMouvement(operation, source.getId(), SensMouvement.DEBIT, request.getMontant(), false);

        Operation saved = operationRepository.save(operation);
        journalService.enregistrer(
                orgId,
                "DEPENSE",
                "Dépense de "
                        + JournalModificationFormatter.montantFcfa(request.getMontant())
                        + " — compte "
                        + compte.name()
                        + (obs != null && !obs.isBlank() ? " — " + obs : "")
                        + " (réf. opération n°"
                        + saved.getId()
                        + ")");
        return toOperationResponse(saved);
    }

    @Transactional
    public OperationResponse enregistrerBanque(Long orgId, BanqueMouvementRequest request, MultipartFile releve) {
        String type = request.getType() != null ? request.getType().trim().toLowerCase() : "";
        if (!"vers".equals(type) && !"ret".equals(type)) {
            throw new BusinessException("Type d'opération bancaire invalide (vers ou ret)");
        }

        TypeOperation typeOp = "vers".equals(type) ? TypeOperation.BANQUE_VERSEMENT : TypeOperation.BANQUE_RETRAIT;
        Compte caisse = compteService.getCompteOrg(orgId, TypeCompte.CAISSE);
        Compte banque = compteService.getCompteOrg(orgId, TypeCompte.BANQUE);

        Long exerciceId = exerciceService.requireExerciceCourantId(orgId);
        operationPlanadGuardService.verifierDateOperationAutorisee(orgId, exerciceId, request.getDateOperation());
        Operation operation = Operation.builder()
                .organisationId(orgId)
                .exerciceId(exerciceId)
                .typeOperation(typeOp)
                .montant(request.getMontant())
                .dateOperation(request.getDateOperation())
                .observation(construireObservationBanque(request, type))
                .utilisateurId(OrganisationContext.getUserId())
                .build();

        if ("vers".equals(type)) {
            ajouterMouvement(operation, caisse.getId(), SensMouvement.DEBIT, request.getMontant(), false);
            ajouterMouvement(operation, banque.getId(), SensMouvement.CREDIT, request.getMontant(), true);
        } else {
            ajouterMouvement(operation, banque.getId(), SensMouvement.DEBIT, request.getMontant(), false);
            ajouterMouvement(operation, caisse.getId(), SensMouvement.CREDIT, request.getMontant(), true);
        }

        Operation saved = operationRepository.save(operation);
        releveBancaireStorageService.enregistrer(orgId, saved.getId(), releve);
        String sens = "vers".equals(type) ? "Versement caisse → banque" : "Retrait banque → caisse";
        journalService.enregistrer(
                orgId,
                typeOp.name(),
                sens
                        + " de "
                        + JournalModificationFormatter.montantFcfa(request.getMontant())
                        + (operation.getObservation() != null ? " — " + operation.getObservation() : "")
                        + " (réf. opération n°"
                        + saved.getId()
                        + ")");
        return toOperationResponse(saved);
    }

    private Map<Long, ReleveBancaire> chargerRelevesParOperation(List<Operation> banqueOps) {
        if (banqueOps.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = banqueOps.stream().map(Operation::getId).toList();
        return releveBancaireRepository.findByOperationIdIn(ids).stream()
                .collect(Collectors.toMap(ReleveBancaire::getOperationId, r -> r, (a, b) -> a));
    }

    private void ajouterMouvement(
            Operation operation,
            Long compteId,
            SensMouvement sens,
            BigDecimal montant,
            boolean autoriserSoldeNegatif) {
        MouvementCompte mc = MouvementCompte.builder()
                .operation(operation)
                .compteId(compteId)
                .sens(sens)
                .montant(montant)
                .build();
        operation.getMouvements().add(mc);
        compteService.appliquerMouvement(compteId, sens, montant, autoriserSoldeNegatif);
    }

    private TypeCompte compteDebite(String code) {
        if (code == null) {
            return TypeCompte.CAISSE;
        }
        return switch (code.trim().toLowerCase()) {
            case "banque" -> TypeCompte.BANQUE;
            case "caisse" -> TypeCompte.CAISSE;
            default -> throw new BusinessException("Compte débité invalide (caisse ou banque)");
        };
    }

    private void validerCategorie(String categorieId) {
        if (categorieId == null || !CATEGORIES.containsKey(categorieId.toLowerCase())) {
            throw new BusinessException("Catégorie de dépense invalide");
        }
    }

    private String construireObservationDepense(DepenseRequest r) {
        String cat = r.getCategorieId().toLowerCase();
        StringBuilder sb = new StringBuilder("[cat:").append(cat).append("]");
        if (r.getBeneficiaire() != null && !r.getBeneficiaire().isBlank()) {
            sb.append(" Bénéficiaire: ").append(r.getBeneficiaire().trim());
        }
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            if (sb.length() > cat.length() + 6) {
                sb.append(" — ");
            } else {
                sb.append(" ");
            }
            sb.append(r.getDescription().trim());
        }
        return sb.toString();
    }

    private String construireObservationBanque(BanqueMouvementRequest r, String type) {
        StringBuilder sb = new StringBuilder("[").append(type).append("]");
        if (r.getReference() != null && !r.getReference().isBlank()) {
            sb.append(" Réf: ").append(r.getReference().trim());
        }
        if (r.getBanqueAgence() != null && !r.getBanqueAgence().isBlank()) {
            sb.append(" | ").append(r.getBanqueAgence().trim());
        }
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            sb.append(" — ").append(r.getDescription().trim());
        }
        if (r.getContreSigne() != null && !r.getContreSigne().isBlank()) {
            sb.append(" | Contre-signé: ").append(r.getContreSigne().trim());
        }
        return sb.toString();
    }

    private DepenseLigneResponse toDepenseLigne(Operation op) {
        ParsedCat cat = parseCategorie(op.getObservation());
        CategorieMeta meta = CATEGORIES.getOrDefault(cat.categorieId(), CATEGORIES.get("autre"));
        return DepenseLigneResponse.builder()
                .id(op.getId())
                .categorieId(cat.categorieId())
                .categorieLabel(meta.icon() + " " + meta.label())
                .montant(op.getMontant())
                .dateOperation(op.getDateOperation())
                .beneficiaire(cat.beneficiaire())
                .description(cat.description())
                .build();
    }

    private List<MouvementCaisseLigneResponse> construireMouvementsCaisse(
            List<MouvementCompte> mouvements, BigDecimal soldeActuel) {
        List<Operation> operations =
                mouvements.stream().map(MouvementCompte::getOperation).toList();
        JournalCaisseLibelleFormatter.Context libelleCtx =
                JournalCaisseLibelleFormatter.buildContext(operations, membreRepository, empruntRepository);
        BigDecimal running = soldeActuel != null ? soldeActuel : BigDecimal.ZERO;
        List<MouvementCaisseLigneResponse> lignes = new ArrayList<>();
        for (MouvementCompte mc : mouvements) {
            Operation op = mc.getOperation();
            String sensUi = mc.getSens() == SensMouvement.CREDIT ? "credit" : "debit";
            lignes.add(MouvementCaisseLigneResponse.builder()
                    .id(op.getId())
                    .dateOperation(op.getDateOperation())
                    .sens(sensUi)
                    .montant(mc.getMontant())
                    .soldeCaisseApres(running)
                    .typeOperation(libelleTypeOperation(op.getTypeOperation()))
                    .libelle(libelleMouvementCaisse(op, libelleCtx))
                    .build());
            if (mc.getSens() == SensMouvement.CREDIT) {
                running = running.subtract(mc.getMontant());
            } else {
                running = running.add(mc.getMontant());
            }
        }
        return lignes;
    }

    private String libelleTypeOperation(TypeOperation type) {
        if (type == null) {
            return "Opération";
        }
        return switch (type) {
            case DEPENSE -> "Dépense";
            case BANQUE_VERSEMENT -> "Versement → Banque";
            case BANQUE_RETRAIT -> "Retrait banque";
            case COTISATION -> "Cotisation";
            case COTISATION_MOIS -> "Cotisation mensuelle";
            case VERSEMENT -> "Versement";
            case REMBOURSEMENT -> "Remboursement";
            case EMPRUNT -> "Emprunt";
            case PENALITE -> "Pénalité";
            case AMENDE -> "Amende";
            case REPARTITION_EXERCICE -> "Répartition clôture exercice";
        };
    }

    private String libelleMouvementCaisse(Operation op, JournalCaisseLibelleFormatter.Context libelleCtx) {
        if (op.getTypeOperation() == TypeOperation.DEPENSE) {
            ParsedCat cat = parseCategorie(op.getObservation());
            CategorieMeta meta = CATEGORIES.getOrDefault(cat.categorieId(), CATEGORIES.get("autre"));
            StringBuilder sb = new StringBuilder(meta.label());
            if (cat.description() != null && !cat.description().isBlank()) {
                sb.append(" — ").append(cat.description());
            }
            String paiement = extrairePaiementCourt(op);
            if (paiement != null) {
                sb.append(" · ").append(paiement);
            }
            return sb.toString();
        }
        if (op.getTypeOperation() == TypeOperation.BANQUE_VERSEMENT
                || op.getTypeOperation() == TypeOperation.BANQUE_RETRAIT) {
            ParsedBk bk = parseBanque(op.getObservation(), op.getTypeOperation());
            String base = bk.description() != null && !bk.description().isBlank()
                    ? bk.description()
                    : libelleTypeOperation(op.getTypeOperation());
            String paiement = extrairePaiementCourt(op);
            if (paiement != null && !base.toLowerCase().contains(paiement.toLowerCase())) {
                return base + " · " + paiement;
            }
            return base;
        }
        return JournalCaisseLibelleFormatter.format(op, libelleCtx);
    }

    private static String extrairePaiementCourt(Operation op) {
        if (op.getModePaiement() != null) {
            return com.cotisapp.util.ModePaiementHelper.libelle(op.getModePaiement());
        }
        return null;
    }

    private List<MouvementBanqueLigneResponse> construireMouvementsBanque(
            List<Operation> banqueOps,
            BigDecimal soldeActuel,
            Map<Long, ReleveBancaire> relevesParOperation) {
        BigDecimal running = soldeActuel != null ? soldeActuel : BigDecimal.ZERO;
        List<MouvementBanqueLigneResponse> lignes = new ArrayList<>();
        for (Operation op : banqueOps) {
            ParsedBk bk = parseBanque(op.getObservation(), op.getTypeOperation());
            ReleveBancaire releve = relevesParOperation.get(op.getId());
            lignes.add(MouvementBanqueLigneResponse.builder()
                    .id(op.getId())
                    .dateOperation(op.getDateOperation())
                    .type(bk.type())
                    .montant(op.getMontant())
                    .soldeBanqueApres(running)
                    .reference(bk.reference())
                    .description(bk.description())
                    .releveId(releve != null ? releve.getId() : null)
                    .releveNomFichier(releve != null ? releve.getNomFichier() : null)
                    .build());
            if (TypeOperation.BANQUE_VERSEMENT.equals(op.getTypeOperation())) {
                running = running.subtract(op.getMontant());
            } else {
                running = running.add(op.getMontant());
            }
        }
        return lignes;
    }

    private List<DepenseParCategorieResponse> agregerParCategorie(List<Operation> depensesMois) {
        Map<String, BigDecimal> totaux = new LinkedHashMap<>();
        for (Operation op : depensesMois) {
            String catId = parseCategorie(op.getObservation()).categorieId();
            totaux.merge(catId, op.getMontant(), BigDecimal::add);
        }
        return totaux.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    CategorieMeta meta = CATEGORIES.getOrDefault(e.getKey(), CATEGORIES.get("autre"));
                    return DepenseParCategorieResponse.builder()
                            .categorieId(e.getKey())
                            .icon(meta.icon())
                            .label(meta.label())
                            .montant(e.getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ParsedCat parseCategorie(String observation) {
        if (observation == null || observation.isBlank()) {
            return new ParsedCat("autre", null, observation);
        }
        Matcher m = CAT_PATTERN.matcher(observation.trim());
        if (!m.find()) {
            return new ParsedCat("autre", null, observation);
        }
        String catId = m.group(1).toLowerCase();
        String reste = m.group(2) != null ? m.group(2).trim() : "";
        String benef = null;
        String desc = reste;
        if (reste.startsWith("Bénéficiaire:")) {
            int sep = reste.indexOf(" — ");
            if (sep > 0) {
                benef = reste.substring("Bénéficiaire:".length(), sep).trim();
                desc = reste.substring(sep + 3).trim();
            } else {
                benef = reste.substring("Bénéficiaire:".length()).trim();
                desc = "";
            }
        }
        return new ParsedCat(catId, benef, desc.isEmpty() ? null : desc);
    }

    private ParsedBk parseBanque(String observation, TypeOperation type) {
        String typeUi = type == TypeOperation.BANQUE_VERSEMENT ? "vers" : "ret";
        String ref = null;
        String desc = observation;
        if (observation != null) {
            Matcher m = BK_TYPE_PATTERN.matcher(observation.trim());
            if (m.find()) {
                typeUi = m.group(1).toLowerCase();
                desc = m.group(2) != null ? m.group(2).trim() : "";
            }
            if (desc.startsWith("Réf:")) {
                int pipe = desc.indexOf('|');
                int dash = desc.indexOf(" — ");
                int end = pipe > 0 ? pipe : (dash > 0 ? dash : desc.length());
                ref = desc.substring("Réf:".length(), end).trim();
                if (pipe > 0) {
                    desc = desc.substring(pipe + 1).trim();
                } else if (dash > 0) {
                    desc = desc.substring(dash + 3).trim();
                }
            }
        }
        return new ParsedBk(typeUi, ref, desc);
    }

    private BigDecimal soldeOrg(Long orgId, TypeCompte type) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(
                        orgId, type, com.cotisapp.domain.enums.ProprietaireCompte.ORGANISATION)
                .map(c -> c.getSolde() != null ? c.getSolde() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal soldeOrgOptional(Long orgId, TypeCompte type) {
        return compteRepository
                .findByOrganisationIdAndTypeCompteAndProprietaire(
                        orgId, type, com.cotisapp.domain.enums.ProprietaireCompte.ORGANISATION)
                .map(c -> c.getSolde() != null ? c.getSolde() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    private OperationResponse toOperationResponse(Operation op) {
        return OperationResponse.builder()
                .id(op.getId())
                .typeOperation(op.getTypeOperation())
                .montant(op.getMontant())
                .dateOperation(op.getDateOperation())
                .observation(op.getObservation())
                .build();
    }

    private record CategorieMeta(String icon, String label) {}

    private record ParsedCat(String categorieId, String beneficiaire, String description) {}

    private record ParsedBk(String type, String reference, String description) {}
}
