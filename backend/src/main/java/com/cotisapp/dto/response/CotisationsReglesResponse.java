package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CotisationsReglesResponse {
    private RegleOperationResponse hebdomadaire;
    private RegleOperationResponse mensuelle;
}
