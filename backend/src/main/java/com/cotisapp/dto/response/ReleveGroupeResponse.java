package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ReleveGroupeResponse {
    private String label;
    private LocalDate date;
    private List<ReleveLigneResponse> lignes;
}
