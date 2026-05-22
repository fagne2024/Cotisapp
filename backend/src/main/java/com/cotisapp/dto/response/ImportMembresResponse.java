package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ImportMembresResponse {

    private int lignesLues;
    private int membresCrees;

    @Builder.Default
    private List<ImportMembreLigneErreur> erreurs = new ArrayList<>();
}
