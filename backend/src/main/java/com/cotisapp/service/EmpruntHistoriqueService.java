package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.StatutEmprunt;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.EmpruntHistoriqueLigneResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpruntHistoriqueService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final OperationRepository operationRepository;
    private final MembreRepository membreRepository;
    private final EmpruntRepository empruntRepository;

    @Transactional(readOnly = true)
    public List<EmpruntHistoriqueLigneResponse> historique(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));

        List<Operation> operations = operationRepository
                .findByOrganisationIdAndTypeOperationInAndOperationOrigineIdIsNullOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.EMPRUNT));
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
        Map<Long, Emprunt> emprunts = empruntRepository.findByOrganisationId(orgId).stream()
                .filter(e -> empruntIds.contains(e.getId()))
                .collect(Collectors.toMap(Emprunt::getId, e -> e));

        Set<Long> empruntsAvecRemboursement = operations.stream()
                .map(Operation::getEmpruntId)
                .filter(Objects::nonNull)
                .filter(id -> operationRepository.existsByEmpruntIdAndTypeOperationAndAnnuleeFalseAndOperationOrigineIdIsNull(
                        id, TypeOperation.REMBOURSEMENT))
                .collect(Collectors.toSet());

        List<EmpruntHistoriqueLigneResponse> lignes = new ArrayList<>();
        for (Operation op : operations) {
            Membre membre = op.getMembreId() != null ? membres.get(op.getMembreId()) : null;
            Emprunt emprunt = op.getEmpruntId() != null ? emprunts.get(op.getEmpruntId()) : null;
            TypeEmprunt typeEmp = emprunt != null ? emprunt.getTypeEmprunt() : TypeEmprunt.ETALE;

            BigDecimal capital = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
            BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
            boolean annulee = Boolean.TRUE.equals(op.getAnnulee());
            boolean annulable = !annulee
                    && !operationRepository.existsByOperationOrigineId(op.getId())
                    && op.getEmpruntId() != null
                    && !empruntsAvecRemboursement.contains(op.getEmpruntId())
                    && emprunt != null
                    && emprunt.getStatut() != StatutEmprunt.ANNULE;
            int nbEch = emprunt != null && emprunt.getEcheances() != null ? emprunt.getEcheances().size() : 0;

            lignes.add(EmpruntHistoriqueLigneResponse.builder()
                    .ligneId(op.getId() + "-emp")
                    .operationId(op.getId())
                    .empruntId(op.getEmpruntId())
                    .typeEmprunt(typeEmp.name())
                    .typeLibelle(RemboursementHistoriqueService.libelleType(typeEmp))
                    .membreId(membre != null ? membre.getId() : null)
                    .membreNom(membre != null ? membre.getNomComplet() : "—")
                    .codeMembre(membre != null ? membre.getCodeMembre() : "")
                    .montantCapital(capital)
                    .montantFrais(frais)
                    .montantTotal(capital.add(frais))
                    .nbEcheances(nbEch > 0 ? nbEch : null)
                    .dateOperation(op.getDateOperation())
                    .dateLabel(op.getDateOperation().format(DATE_LABEL))
                    .observation(op.getObservation())
                    .annulee(annulee)
                    .annulable(annulable)
                    .build());
        }
        return lignes;
    }
}
