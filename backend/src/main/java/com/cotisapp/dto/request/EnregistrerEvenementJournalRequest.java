package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.TypeEvenementJournal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnregistrerEvenementJournalRequest {

    @NotNull
    private TypeEvenementJournal typeEvenement;

    @NotBlank
    private String action;

    private String moduleCode;
    private String moduleLibelle;
    private String routePath;
    private String details;
    private Boolean succes;
}
