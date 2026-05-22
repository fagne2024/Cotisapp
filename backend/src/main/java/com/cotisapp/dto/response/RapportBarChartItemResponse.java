package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RapportBarChartItemResponse {
    private String label;
    private String valeurLabel;
    private int heightPct;
    private boolean belowTarget;
}
