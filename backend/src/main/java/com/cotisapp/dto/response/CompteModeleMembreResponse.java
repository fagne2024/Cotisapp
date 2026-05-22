package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompteModeleMembreResponse {
    private Long id;
    private String code;
    private String libelle;
    private Boolean actif;
}
