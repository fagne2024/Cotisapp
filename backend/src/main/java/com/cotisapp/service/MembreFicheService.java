package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.CompteModeleMembre;
import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.domain.entity.SuiviMensuel;
import com.cotisapp.dto.response.CompteMembreResponse;
import com.cotisapp.dto.response.MembreResponse;
import com.cotisapp.dto.response.MembreSoldeMembreResponse;
import com.cotisapp.dto.response.MembreSoldesResponse;
import com.cotisapp.dto.response.MonCompteFicheResponse;
import com.cotisapp.dto.response.OperationResponse;
import com.cotisapp.dto.response.SuiviMensuelResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.security.OrganisationContext;
import com.cotisapp.repository.CompteModeleMembreRepository;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.SuiviMensuelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembreFicheService {

    private final MembreRepository membreRepository;
    private final CompteRepository compteRepository;
    private final CompteModeleMembreRepository compteModeleMembreRepository;
    private final OperationRepository operationRepository;
    private final EmpruntRepository empruntRepository;
    private final SuiviMensuelRepository suiviMensuelRepository;
    private final OperationMapperService operationMapperService;
    private final CompteService compteService;
    private final ParametrageCompteService parametrageCompteService;
    private final MembreService membreService;
    private final EmpruntService empruntService;

    @Transactional(readOnly = true)
    public List<MembreSoldesResponse> listerSoldesComptes(Long orgId) {
        Map<Long, MembreSoldesResponse> result = new HashMap<>();
        for (Membre m : membreRepository.findByOrganisationId(orgId)) {
            result.put(m.getId(), soldesVides(m.getId()));
        }

        for (Compte c : compteRepository.findByOrganisationId(orgId)) {
            if (c.getProprietaire() != ProprietaireCompte.MEMBRE || c.getMembreId() == null) {
                continue;
            }
            if (c.getTypeCompte() == TypeCompte.CUSTOM) {
                continue;
            }
            Long membreId = c.getMembreId();
            MembreSoldesResponse row = result.computeIfAbsent(membreId, this::soldesVides);
            BigDecimal solde = c.getSolde() != null ? c.getSolde() : BigDecimal.ZERO;
            switch (c.getTypeCompte()) {
                case EPARGNE_HEBDO, EPARGNE -> row.setEpargneHebdo(row.getEpargneHebdo().add(solde));
                case EPARGNE_MOIS -> row.setEpargneMois(row.getEpargneMois().add(solde));
                case SOLIDARITE -> row.setSolidarite(row.getSolidarite().add(solde));
                case PENALITE -> row.setPenalite(row.getPenalite().add(solde));
                case AMENDE -> row.setAmende(row.getAmende().add(solde));
                case INTERET -> { }
                case DEPENSE -> row.setDepense(nzDepense(row).add(solde));
                default -> { }
            }
        }

        return new ArrayList<>(result.values());
    }

    private MembreSoldesResponse soldesVides(Long membreId) {
        return MembreSoldesResponse.builder()
                .membreId(membreId)
                .epargneHebdo(BigDecimal.ZERO)
                .epargneMois(BigDecimal.ZERO)
                .solidarite(BigDecimal.ZERO)
                .penalite(BigDecimal.ZERO)
                .amende(BigDecimal.ZERO)
                .depense(BigDecimal.ZERO)
                .build();
    }

    private static BigDecimal nzDepense(MembreSoldesResponse row) {
        return row.getDepense() != null ? row.getDepense() : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public MembreSoldeMembreResponse calculerSoldeMembre(Long orgId, Long membreId) {
        getMembre(orgId, membreId);

        BigDecimal epargne = BigDecimal.ZERO;
        BigDecimal solidarite = BigDecimal.ZERO;
        for (Compte c : compteRepository.findByMembreId(membreId)) {
            if (c.getProprietaire() != ProprietaireCompte.MEMBRE) {
                continue;
            }
            BigDecimal solde = c.getSolde() != null ? c.getSolde() : BigDecimal.ZERO;
            switch (c.getTypeCompte()) {
                case EPARGNE_HEBDO -> epargne = epargne.add(solde);
                case EPARGNE_MOIS -> epargne = epargne.add(solde);
                case EPARGNE -> epargne = epargne.add(solde);
                case SOLIDARITE -> solidarite = solidarite.add(solde);
                default -> { }
            }
        }

        BigDecimal totalEmprunts = BigDecimal.ZERO;
        BigDecimal fraisEmprunt = BigDecimal.ZERO;
        for (Emprunt e : empruntRepository.findByMembreIdAndOrganisationId(membreId, orgId)) {
            totalEmprunts = totalEmprunts.add(e.getMontantTotal() != null ? e.getMontantTotal() : BigDecimal.ZERO);
            if (e.getMontantFrais() != null) {
                fraisEmprunt = fraisEmprunt.add(e.getMontantFrais());
            }
        }

        BigDecimal remboursements = BigDecimal.ZERO;
        BigDecimal fraisRemboursement = BigDecimal.ZERO;
        for (Operation op : operationRepository.findByMembreIdOrderByDateCreationDesc(membreId)) {
            if (op.getTypeOperation() != TypeOperation.REMBOURSEMENT) {
                continue;
            }
            remboursements = remboursements.add(op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO);
            if (op.getMontantFrais() != null) {
                fraisRemboursement = fraisRemboursement.add(op.getMontantFrais());
            }
        }

        // Remboursements et emprunts sont reflétés par les mouvements sur les comptes membre.
        BigDecimal solde = epargne.add(solidarite);

        return MembreSoldeMembreResponse.builder()
                .membreId(membreId)
                .solde(solde)
                .epargne(epargne)
                .solidarite(solidarite)
                .emprunts(totalEmprunts)
                .fraisEmprunt(fraisEmprunt)
                .remboursements(remboursements)
                .fraisRemboursement(fraisRemboursement)
                .build();
    }

    @Transactional
    public List<CompteMembreResponse> listerComptes(Long orgId, Long membreId) {
        Membre membre = getMembre(orgId, membreId);
        parametrageCompteService.initialiserParametrageParDefaut(orgId);
        compteService.assurerComptesMembreSelonParametrage(orgId, membre.getId());
        Map<Long, CompteModeleMembre> modeles =
                compteModeleMembreRepository.findByOrganisationIdOrderByLibelleAsc(orgId).stream()
                .collect(Collectors.toMap(CompteModeleMembre::getId, m -> m));

        return compteRepository.findByMembreId(membre.getId()).stream()
                .filter(c -> c.getProprietaire() == ProprietaireCompte.MEMBRE)
                .filter(c -> c.getTypeCompte() != TypeCompte.DEPENSE)
                .sorted(Comparator.comparing(c -> ordreType(c.getTypeCompte())))
                .map(c -> toCompteResponse(c, modeles.get(c.getModeleCompteId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonCompteFicheResponse chargerFicheMonCompte(Long orgId, Long membreId, String moisAnnee) {
        Membre membre = getMembre(orgId, membreId);
        SuiviMensuel suivi = suiviMensuelRepository
                .findByMembreIdAndMoisAnnee(membreId, moisAnnee)
                .orElse(null);
        return MonCompteFicheResponse.builder()
                .membre(membreService.toResponse(membre))
                .comptes(listerComptes(orgId, membreId))
                .operations(listerOperations(orgId, membreId))
                .emprunts(empruntService.listerParMembre(orgId, membreId))
                .suiviMensuel(suivi != null ? toSuiviResponse(suivi, membre) : null)
                .solde(calculerSoldeMembre(orgId, membreId))
                .build();
    }

    private SuiviMensuelResponse toSuiviResponse(SuiviMensuel s, Membre m) {
        return SuiviMensuelResponse.builder()
                .id(s.getId())
                .membreId(s.getMembreId())
                .membreNom(m.getNomComplet())
                .codeMembre(m.getCodeMembre())
                .moisAnnee(s.getMoisAnnee())
                .montantDu(s.getMontantDu())
                .montantPaye(s.getMontantPaye())
                .statut(s.getStatut())
                .build();
    }

    @Transactional(readOnly = true)
    public List<OperationResponse> listerOperations(Long orgId, Long membreId) {
        getMembre(orgId, membreId);
        Long compteSolidariteId = compteRepository
                .findByMembreIdAndTypeCompte(membreId, TypeCompte.SOLIDARITE)
                .map(Compte::getId)
                .orElse(null);
        return operationRepository.findByMembreIdWithMouvementsOrderByDateCreationDesc(membreId).stream()
                .filter(op -> !Boolean.TRUE.equals(op.getAnnulee()))
                .map(op -> operationMapperService.toResponse(op, compteSolidariteId))
                .toList();
    }

    private Membre getMembre(Long orgId, Long membreId) {
        Membre membre = membreRepository
                .findByIdAndOrganisationId(membreId, orgId)
                .orElseThrow(() -> new BusinessException("Membre introuvable"));
        if (OrganisationContext.getRole() == Role.MEMBRE) {
            Long ctxMembreId = OrganisationContext.getMembreId();
            if (ctxMembreId == null || !Objects.equals(ctxMembreId, membreId)) {
                throw new BusinessException("Accès refusé à cette fiche membre");
            }
            Long uid = OrganisationContext.getUserId();
            if (uid != null && !Objects.equals(membre.getUtilisateurId(), uid)) {
                throw new BusinessException("Accès refusé à cette fiche membre");
            }
        }
        return membre;
    }

    private CompteMembreResponse toCompteResponse(Compte c, CompteModeleMembre modele) {
        String libelle = c.getLibelle();
        if (libelle == null || libelle.isBlank()) {
            libelle = libelleParDefaut(c.getTypeCompte(), modele);
        }
        return CompteMembreResponse.builder()
                .id(c.getId())
                .typeCompte(c.getTypeCompte())
                .libelle(libelle)
                .solde(c.getSolde())
                .modeleCompteId(c.getModeleCompteId())
                .modeleCode(modele != null ? modele.getCode() : null)
                .build();
    }

    private static String libelleParDefaut(TypeCompte type, CompteModeleMembre modele) {
        if (modele != null) {
            return modele.getLibelle();
        }
        return switch (type) {
            case EPARGNE, EPARGNE_HEBDO -> ParametrageCompteService.libelleDefaut(
                    com.cotisapp.domain.enums.FamilleCompte.EPARGNE_HEBDO);
            case EPARGNE_MOIS -> ParametrageCompteService.libelleDefaut(
                    com.cotisapp.domain.enums.FamilleCompte.EPARGNE_MOIS);
            case SOLIDARITE -> ParametrageCompteService.libelleDefaut(
                    com.cotisapp.domain.enums.FamilleCompte.SOLIDARITE);
            case PENALITE -> "Pénalité";
            case AMENDE -> "Amende";
            case CUSTOM -> "Compte personnalisé";
            default -> type.name();
        };
    }

    private static boolean estTypeEpargne(TypeCompte type) {
        return type == TypeCompte.EPARGNE
                || type == TypeCompte.EPARGNE_HEBDO
                || type == TypeCompte.EPARGNE_MOIS;
    }

    private static int ordreType(TypeCompte type) {
        return switch (type) {
            case EPARGNE_HEBDO, EPARGNE -> 1;
            case EPARGNE_MOIS -> 2;
            case SOLIDARITE -> 3;
            case PENALITE -> 4;
            case AMENDE -> 5;
            case CUSTOM -> 6;
            default -> 99;
        };
    }
}
