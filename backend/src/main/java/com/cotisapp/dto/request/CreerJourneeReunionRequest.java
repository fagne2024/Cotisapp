package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreerJourneeReunionRequest {
    @NotNull
    private LocalDate dateReunion;
}
