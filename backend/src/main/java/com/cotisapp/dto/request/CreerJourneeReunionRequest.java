package com.cotisapp.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreerJourneeReunionRequest {
    private LocalDate dateReunion;
}
