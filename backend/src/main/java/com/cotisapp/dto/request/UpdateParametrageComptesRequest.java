package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.FamilleCompte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateParametrageComptesRequest {
    @NotNull
    @Valid
    private Map<FamilleCompte, UpdateParametrageCompteRequest> comptes;
}
