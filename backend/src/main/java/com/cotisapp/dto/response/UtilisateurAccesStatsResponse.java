package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UtilisateurAccesStatsResponse {
    private long total;
    private long actifs;
    private long suspendus;
    private long connectesMaintenant;
}
