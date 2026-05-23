package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BulkPaiementMobileMembreResponse {
    private int nombreMisAJour;
    private boolean actif;
    private String message;
}
