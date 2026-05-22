package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProfilActiviteResponse {
    private Long id;
    private String action;
    private String details;
    private String libelle;
    private LocalDateTime dateCreation;
}
