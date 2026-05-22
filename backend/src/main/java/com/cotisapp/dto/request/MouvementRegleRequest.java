package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.SensMouvement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MouvementRegleRequest {
    private Long id;

    @NotNull
    private Integer ordre;

    @NotBlank
    private String sourceType;

    @NotBlank
    private String cibleType;

    @NotNull
    private SensMouvement sens;

    @NotBlank
    private String typeMontant;
}
