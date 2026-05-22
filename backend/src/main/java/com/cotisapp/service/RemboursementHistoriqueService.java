package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.RemboursementHistoriqueLigneResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import com.cotisapp.util.ModePaiementHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RemboursementHistoriqueService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final OperationRepository operationRepository;
    private final MembreRepository membreRepository;
    private final EmpruntRepository empruntRepository;

    @Transactional(readOnly = true)
    public List<RemboursementHistoriqueLigneResponse> historique(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));

        List<Operation> operations = operationRepository
                .findByOrganisationIdAndTypeOperationInAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.REMBOURSEMENT));
        if (operations.isEmpty()) {
            return List.of();
        }

        Set<Long> membreIds = operations.stream()
                .map(Operation::getMembreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Membre> membres = membreRepository.findAllById(membreIds).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));

        Set<Long> empruntIds = operations.stream()
                .map(Operation::getEmpruntId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Emprunt> emprunts = empruntRepository.findAllById(empruntIds).stream()
                .collect(Collectors.toMap(Emprunt::getId, e -> e));

        Map<Long, Long> dernierRembParEmprunt = new HashMap<>();
        for (Operation op : operations) {
            if (op.getEmpruntId() != null && !dernierRembParEmprunt.containsKey(op.getEmpruntId())) {
                dernierRembParEmprunt.put(op.getEmpruntId(), op.getId());
            }
        }

        List<RemboursementHistoriqueLigneResponse> lignes = new ArrayList<>();
        for (Operation op : operations) {
            Membre membre = op.getMembreId() != null ? membres.get(op.getMembreId()) : null;
            Emprunt emprunt = op.getEmpruntId() != null ? emprunts.get(op.getEmpruntId()) : null;
            TypeEmprunt typeEmp = emprunt != null ? emprunt.getTypeEmprunt() : TypeEmprunt.ETALE;

            BigDecimal capital = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
            BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
            BigDecimal penalite = extrairePenalite(op.getObservation());
            boolean annulee = Boolean.TRUE.equals(op.getAnnulee());
            boolean annulable = !annulee
                    && !operationRepository.existsByOperationOrigineId(op.getId())
                    && op.getEmpruntId() != null
                    && Objects.equals(dernierRembParEmprunt.get(op.getEmpruntId()), op.getId())
                    && emprunt != null
                    && emprunt.getStatut() != StatutEmprunt.ANNULE;

            lignes.add(RemboursementHistoriqueLigneResponse.builder()
                    .ligneId(op.getId() + "-remb")
                    .operationId(op.getId())
                    .empruntId(op.getEmpruntId())
                    .typeEmprunt(typeEmp.name())
                    .typeLibelle(libelleType(typeEmp))
                    .membreId(membre != null ? membre.getId() : null)
                    .membreNom(membre != null ? membre.getNomComplet() : "—")
                    .codeMembre(membre != null ? membre.getCodeMembre() : "")
                    .montantCapital(capital)
                    .montantFrais(frais)
                    .montantPenalite(penalite)
                    .montantTotal(capital.add(frais).add(penalite))
                    .dateOperation(op.getDateOperation())
                    .dateLabel(op.getDateOperation().format(DATE_LABEL))
                    .observation(op.getObservation())
                    .modePaiementLibelle(ModePaiementHelper.libelle(op.getModePaiement()))
                    .referencePaiement(op.getReferencePaiement())
                    .annulee(annulee)
                    .annulable(annulable)
                    .build());
        }
        return lignes;
    }

    static String libelleType(TypeEmprunt type) {
        return switch (type) {
            case ETALE -> "Étalé / Financement";
            case CAISSE -> "Caisse / Financement";
            case SOLIDARITE -> "Solidarité";
        };
    }

    private BigDecimal extrairePenalite(String observation) {
        if (observation == null || !observation.startsWith("Pénalité retard:")) {
            return BigDecimal.ZERO;
        }
        String rest = observation.substring("Pénalité retard:".length()).trim();
        try {
            return new BigDecimal(rest.replace(',', '.'));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
