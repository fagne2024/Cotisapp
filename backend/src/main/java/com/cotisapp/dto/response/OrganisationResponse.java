package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganisationResponse {
    private Long id;
    private String code;
    private String nom;
    private String description;
    private Boolean actif;
    /** Présent si un logo a été enregistré (GET /api/organisations/{id}/logo). */
    private String logoUrl;
}
