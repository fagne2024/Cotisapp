package com.cotisapp.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public record NotificationResponse(
        String id,
        String groupe,
        String severite,
        boolean lu,
        String icone,
        String iconeClass,
        String titre,
        String description,
        String temps,
        String tag,
        String tagClass,
        String actionLabel,
        List<String> actionSegments,
        Map<String, String> actionQueryParams,
        String typeFiltre,
        LocalDateTime dateTri,
        Long demandeId,
        boolean workflowDemande,
        /** True uniquement pour une demande membre encore {@code EN_ATTENTE} (boutons approuver / rejeter). */
        boolean demandeWorkflowActif,
        String demandeTypeDemande,
        boolean amendeApplicable,
        BigDecimal montantAmendeMin,
        BigDecimal montantAmendeMax) {}
