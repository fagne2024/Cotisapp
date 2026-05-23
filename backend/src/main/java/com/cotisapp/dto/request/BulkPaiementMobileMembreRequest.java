package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BulkPaiementMobileMembreRequest {

    @NotEmpty
    private List<Long> membreIds;

    @NotNull
    private Boolean actif;
}
