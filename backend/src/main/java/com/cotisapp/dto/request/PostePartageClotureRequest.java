package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostePartageClotureRequest {
    @NotBlank
    @Size(max = 50)
    private String code;
    @NotBlank
    @Size(max = 255)
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
    /** En mode PRORATA global : appliquer parts / % sur ce poste. */
    private boolean appliquerProrata = true;
}
