package com.cotisapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportMembreLigneErreur {
    private int ligne;
    private String message;
}
