package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RapportEmpruntCardResponse {
    private String nom;
    private String badge;
    private String badgeClass;
    private String detail;
    private String rembourse;
    private String total;
    private int pct;
    private String barClass;
    private String borderClass;
    private String bgClass;
}
