package com.cotisapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SauvegarderTypeProfilDroitsRequest {
    @NotNull
    @Valid
    private List<TypeProfilDroitItemRequest> droits;
}
