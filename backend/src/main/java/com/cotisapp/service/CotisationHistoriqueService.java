package com.cotisapp.service;

import com.cotisapp.domain.entity.Compte;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.MouvementCompte;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.ProprietaireCompte;
import com.cotisapp.domain.enums.SensMouvement;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.CotisationHistoriqueLigneResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.CompteRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.domain.enums.ModePaiement;
import com.cotisapp.util.ModePaiementHelper;
import com.cotisapp.util.SemaineIsoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CotisationHistoriqueService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final OperationRepository operationRepository;
    private final MembreRepository membreRepository;
    private final CompteRepository compteRepository;

    @Transactional(readOnly = true)
    public List<CotisationHistoriqueLigneResponse> historique(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));

        List<TypeOperation> types = List.of(TypeOperation.COTISATION, TypeOperation.COTISATION_MOIS);
        List<Operation> operations = operationRepository
                .findByOrganisationIdAndTypeOperationInAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
                        orgId, types);
        if (operations.isEmpty()) {
            return List.of();
        }

        operations.forEach(op -> op.getMouvements().size());

        Set<Long> membreIds = operations.stream()
                .map(Operation::getMembreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Membre> membres = membreRepository.findAllById(membreIds).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));

        Set<Long> compteIds = operations.stream()
                .flatMap(o -> o.getMouvements().stream())
                .map(MouvementCompte::getCompteId)
                .collect(Collectors.toSet());
        Map<Long, Compte> comptes = compteRepository.findAllById(compteIds).stream()
                .collect(Collectors.toMap(Compte::getId, c -> c));

        List<CotisationHistoriqueLigneResponse> lignes = new ArrayList<>();
        for (Operation op : operations) {
            Membre membre = op.getMembreId() != null ? membres.get(op.getMembreId()) : null;
            String nom = membre != null ? membre.getNomComplet() : "—";
            String code = membre != null ? membre.getCodeMembre() : "";
            Long membreId = membre != null ? membre.getId() : null;
            String dateLabel = op.getDateOperation().format(DATE_LABEL);
            boolean hebdo = op.getTypeOperation() == TypeOperation.COTISATION;
            String typeCotisation = hebdo ? "HEBDO" : "MOIS";
            String periode = hebdo ? periodeHebdo(op.getObservation()) : periodeMois(op.getMoisAnnee());
            boolean annulee = Boolean.TRUE.equals(op.getAnnulee());

            lignes.add(CotisationHistoriqueLigneResponse.builder()
                    .ligneId(op.getId() + "-cot")
                    .operationId(op.getId())
                    .typeLigne(typeCotisation)
                    .typeCotisation(typeCotisation)
                    .typeLibelle(hebdo ? "Cotisation hebdo" : "Cotisation mensuelle")
                    .membreId(membreId)
                    .membreNom(nom)
                    .codeMembre(code)
                    .periode(periode)
                    .montant(op.getMontant())
                    .dateOperation(op.getDateOperation())
                    .dateLabel(dateLabel)
                    .observation(op.getObservation())
                    .modePaiementLibelle(libelleModePaiement(op.getModePaiement()))
                    .referencePaiement(op.getReferencePaiement())
                    .annulee(annulee)
                    .annulable(!annulee)
                    .build());

            for (MouvementCompte mc : op.getMouvements()) {
                Compte compte = comptes.get(mc.getCompteId());
                if (compte == null || mc.getSens() != SensMouvement.CREDIT) {
                    continue;
                }
                if (compte.getTypeCompte() == TypeCompte.SOLIDARITE
                        && compte.getProprietaire() == ProprietaireCompte.ORGANISATION) {
                    lignes.add(ligneMouvement(
                            op, mc, membreId, nom, code, periode, dateLabel, typeCotisation,
                            "SOLIDARITE", "Solidarité", annulee));
                }
            }
        }

        lignes.sort(Comparator
                .comparing(CotisationHistoriqueLigneResponse::getDateOperation).reversed()
                .thenComparing(CotisationHistoriqueLigneResponse::getOperationId, Comparator.reverseOrder())
                .thenComparing(l -> ordreType(l.getTypeLigne())));

        return lignes;
    }

    private CotisationHistoriqueLigneResponse ligneMouvement(
            Operation op,
            MouvementCompte mc,
            Long membreId,
            String nom,
            String code,
            String periode,
            String dateLabel,
            String typeCotisation,
            String typeLigne,
            String typeLibelle,
            boolean annulee) {
        return CotisationHistoriqueLigneResponse.builder()
                .ligneId(op.getId() + "-" + typeLigne.toLowerCase() + "-" + mc.getId())
                .operationId(op.getId())
                .typeLigne(typeLigne)
                .typeCotisation(typeCotisation)
                .typeLibelle(typeLibelle)
                .membreId(membreId)
                .membreNom(nom)
                .codeMembre(code)
                .periode(periode)
                .montant(mc.getMontant())
                .dateOperation(op.getDateOperation())
                .dateLabel(dateLabel)
                .observation(op.getObservation())
                .annulee(annulee)
                .annulable(false)
                .build();
    }

    private int ordreType(String type) {
        return switch (type) {
            case "HEBDO", "MOIS" -> 0;
            case "SOLIDARITE" -> 1;
            default -> 2;
        };
    }

    private String periodeHebdo(String observation) {
        if (observation == null) {
            return "—";
        }
        int start = observation.indexOf('[');
        int end = observation.indexOf(']', start + 1);
        if (start < 0 || end <= start) {
            return "—";
        }
        String key = observation.substring(start + 1, end);
        if (key.matches("\\d{4}-W\\d{1,2}")) {
            try {
                return SemaineIsoUtil.libelleSemaine(key);
            } catch (Exception e) {
                return key;
            }
        }
        return key;
    }

    private String periodeMois(String moisAnnee) {
        if (moisAnnee == null || moisAnnee.isBlank()) {
            return "—";
        }
        try {
            LocalDate d = LocalDate.parse(moisAnnee + "-01");
            String m = d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));
            if (!m.isEmpty()) {
                return Character.toUpperCase(m.charAt(0)) + m.substring(1);
            }
            return m;
        } catch (Exception e) {
            return moisAnnee;
        }
    }

    private static String libelleModePaiement(ModePaiement mode) {
        return ModePaiementHelper.libelle(mode);
    }
}
