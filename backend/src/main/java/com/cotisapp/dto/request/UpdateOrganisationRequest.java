package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateOrganisationRequest {
    @NotBlank
    private String nom;
    private String description;
    private Boolean actif;
}
