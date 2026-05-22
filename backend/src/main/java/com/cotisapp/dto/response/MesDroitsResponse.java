package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.NiveauDroit;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MesDroitsResponse {
    private boolean peutGestion;
    private Map<String, NiveauDroit> actions;
}
