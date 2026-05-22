package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RapportHeroStatResponse {
    private String valeur;
    private String label;
    private String trend;
}
