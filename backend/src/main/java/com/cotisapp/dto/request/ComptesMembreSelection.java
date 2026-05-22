package com.cotisapp.dto.request;

import lombok.Data;

@Data
public class ComptesMembreSelection {
    private boolean epargneHebdo = true;
    private boolean epargneMois = true;
    private boolean solidarite = true;
    private boolean penalite = false;
    private boolean amende = false;
}
