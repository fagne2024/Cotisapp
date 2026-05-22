package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MonCompteFicheResponse {
    private MembreResponse membre;
    private List<CompteMembreResponse> comptes;
    private List<OperationResponse> operations;
    private List<EmpruntResponse> emprunts;
    private SuiviMensuelResponse suiviMensuel;
    private MembreSoldeMembreResponse solde;
}
