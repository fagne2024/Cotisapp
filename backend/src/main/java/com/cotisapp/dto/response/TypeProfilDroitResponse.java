package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.NiveauDroit;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TypeProfilDroitResponse {
    private String actionCode;
    private String section;
    private String libelle;
    private NiveauDroit niveau;
}
