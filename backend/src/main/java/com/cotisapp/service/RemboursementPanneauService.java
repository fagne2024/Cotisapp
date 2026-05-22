package com.cotisapp.service;

import com.cotisapp.domain.entity.Emprunt;
import com.cotisapp.domain.entity.Membre;
import com.cotisapp.domain.entity.Operation;
import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeEmprunt;
import com.cotisapp.domain.enums.TypeOperation;
import com.cotisapp.dto.response.RemboursementPanneauResponse;
import com.cotisapp.dto.response.RemboursementRecentResponse;
import com.cotisapp.exception.BusinessException;
import com.cotisapp.repository.EmpruntRepository;
import com.cotisapp.repository.MembreRepository;
import com.cotisapp.repository.OperationRepository;
import com.cotisapp.repository.OrganisationRepository;
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
public class RemboursementPanneauService {

    private static final int MAX_RECENTES = 8;
    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final OrganisationRepository organisationRepository;
    private final OperationRepository operationRepository;
    private final MembreRepository membreRepository;
    private final EmpruntRepository empruntRepository;
    private final CompteService compteService;

    @Transactional(readOnly = true)
    public RemboursementPanneauResponse panneau(Long orgId) {
        organisationRepository.findById(orgId).orElseThrow(() -> new BusinessException("Organisation introuvable"));

        BigDecimal soldeCaisse = compteService.getCompteOrg(orgId, TypeCompte.CAISSE).getSolde();
        BigDecimal soldeSolidarite = compteService.getCompteOrg(orgId, TypeCompte.SOLIDARITE).getSolde();

        LocalDate debut = LocalDate.now().minusMonths(3);
        List<Operation> ops = operationRepository
                .findByOrganisationIdAndTypeOperationInAndDateOperationBetweenOrderByDateOperationDescDateCreationDesc(
                        orgId, List.of(TypeOperation.REMBOURSEMENT), debut, LocalDate.now())
                .stream()
                .filter(op -> op.getOperationOrigineId() == null)
                .limit(MAX_RECENTES)
                .toList();

        Set<Long> membreIds = ops.stream()
                .map(Operation::getMembreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Membre> membres = membreRepository.findAllById(membreIds).stream()
                .collect(Collectors.toMap(Membre::getId, m -> m));

        Set<Long> empruntIds = ops.stream()
                .map(Operation::getEmpruntId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Emprunt> emprunts = empruntRepository.findAllById(empruntIds).stream()
                .collect(Collectors.toMap(Emprunt::getId, e -> e));

        List<RemboursementRecentResponse> recentes = ops.stream()
                .map(op -> toRecent(op, membres, emprunts))
                .toList();

        return RemboursementPanneauResponse.builder()
                .soldeCaisse(soldeCaisse != null ? soldeCaisse : BigDecimal.ZERO)
                .soldeSolidarite(soldeSolidarite != null ? soldeSolidarite : BigDecimal.ZERO)
                .recentes(recentes)
                .build();
    }

    private RemboursementRecentResponse toRecent(
            Operation op, Map<Long, Membre> membres, Map<Long, Emprunt> emprunts) {
        Membre m = op.getMembreId() != null ? membres.get(op.getMembreId()) : null;
        Emprunt emp = op.getEmpruntId() != null ? emprunts.get(op.getEmpruntId()) : null;
        TypeEmprunt type = emp != null ? emp.getTypeEmprunt() : TypeEmprunt.ETALE;
        String nom = m != null ? m.getNomComplet() : "Membre";
        BigDecimal capital = op.getMontant() != null ? op.getMontant() : BigDecimal.ZERO;
        BigDecimal frais = op.getMontantFrais() != null ? op.getMontantFrais() : BigDecimal.ZERO;
        BigDecimal total = capital.add(frais);

        String iconeClass = switch (type) {
            case SOLIDARITE -> "ico-bl";
            case CAISSE -> "ico-or";
            case ETALE -> "ico-g";
        };

        String meta = op.getDateOperation().format(DATE_LABEL);
        if (frais.compareTo(BigDecimal.ZERO) > 0) {
            meta += " · capital + frais";
        }

        return RemboursementRecentResponse.builder()
                .operationId(op.getId())
                .membreNom(nom)
                .typeEmprunt(type.name())
                .typeLibelle(RemboursementHistoriqueService.libelleType(type))
                .montantTotal(total)
                .dateLabel(op.getDateOperation().format(DATE_LABEL))
                .meta(meta)
                .iconeClass(iconeClass)
                .build();
    }
}
