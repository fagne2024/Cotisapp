package com.cotisapp.dto.request;

import lombok.Data;

/**
 * Comptes proposés à la création d'une organisation.
 * La caisse et le compte intérêts organisation sont toujours créés (non modifiables).
 */
@Data
public class ComptesOrganisationSelection {
    private boolean solidarite = true;
    private boolean epargneHebdo = true;
    private boolean epargneMois = true;
    private boolean penalite = false;
    private boolean amende = false;
    private boolean banque = false;
}
