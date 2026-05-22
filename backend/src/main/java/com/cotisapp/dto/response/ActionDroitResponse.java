package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActionDroitResponse {
    private String code;
    private String section;
    private String libelle;
    private Integer ordre;
}
