package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmpruntsReglesResponse {
    private RegleOperationResponse etale;
    private RegleOperationResponse caisse;
    private RegleOperationResponse solidarite;
}
