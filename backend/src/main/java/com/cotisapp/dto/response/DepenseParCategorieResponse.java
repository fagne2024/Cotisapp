package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DepenseParCategorieResponse {
    private String categorieId;
    private String icon;
    private String label;
    private BigDecimal montant;
}
