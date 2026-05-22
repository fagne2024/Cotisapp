package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RapportParticipationResponse {
    private int pctGlobal;
    private int membresAJour;
    private int membresTotal;
    private int hebdoPayes;
    private int hebdoTotal;
    private int moisPayes;
    private int moisTotal;
    private int bureauPayes;
    private int bureauTotal;
}
