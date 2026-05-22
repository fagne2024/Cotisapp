package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostePartageClotureRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String libelle;
    private boolean actif;
    private boolean builtIn;
    private TypeCompte compteMembre;
    private TypeCompte compteSourceOrg;
    private TypeOperation typeOperation;
    /** 1 ou 2 si regroupement de postes activé. */
    private Integer groupePartage;
    /** En mode ADDITIONNER : inclure ce poste dans le pool additionné. */
    private boolean inclureDansPoolAdditionne;
}
