package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.NiveauDroit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TypeProfilDroitItemRequest {
    @NotBlank
    private String actionCode;

    @NotNull
    private NiveauDroit niveau;
}
