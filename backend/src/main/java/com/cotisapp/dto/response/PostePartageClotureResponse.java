package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.TypeCompte;
import com.cotisapp.domain.enums.TypeOperation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PostePartageClotureResponse {
    private String code;
    private String libelle;
    private boolean actif;
    private boolean builtIn;
    private TypeCompte compteMembre;
    private TypeCompte compteSourceOrg;
    private TypeOperation typeOperation;
    private Integer groupePartage;
    private boolean inclureDansPoolAdditionne;
    private BigDecimal montantPool;
    private BigDecimal montantDistribue;
}
