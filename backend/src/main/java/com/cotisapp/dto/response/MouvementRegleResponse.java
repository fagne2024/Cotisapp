package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.SensMouvement;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MouvementRegleResponse {
    private Long id;
    private Integer ordre;
    private String sourceType;
    private String cibleType;
    private SensMouvement sens;
    private String typeMontant;
}
