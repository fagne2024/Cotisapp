package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompteMembreLoginDto {
    private Long membreId;
    private Long organisationId;
    private String organisationNom;
    private String organisationCode;
    private String codeMembre;
    private String nomComplet;
}
