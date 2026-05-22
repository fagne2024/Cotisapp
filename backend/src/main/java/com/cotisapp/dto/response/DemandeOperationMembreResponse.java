package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.DemandeOperationStatut;
import com.cotisapp.domain.enums.DemandeOperationType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record DemandeOperationMembreResponse(
        Long id,
        Long membreId,
        String membreNom,
        String codeMembre,
        DemandeOperationType typeDemande,
        DemandeOperationStatut statut,
        BigDecimal montant,
        String modePaiement,
        String referencePaiement,
        String libelleResume,
        LocalDateTime dateDemande,
        LocalDateTime dateTraitement,
        String motifRefus,
        Long operationId,
        String message) {}
