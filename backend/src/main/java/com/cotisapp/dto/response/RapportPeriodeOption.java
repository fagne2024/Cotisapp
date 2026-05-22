package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RapportPeriodeOption {
    private String value;
    private String label;
}
