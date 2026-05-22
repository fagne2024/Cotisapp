package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTypeProfilRequest {
    @Size(max = 120)
    private String libelle;
    private PosteMembre posteMembre;
    private CanalConnexion canalConnexion;
    private Boolean actif;
    private Integer ordre;
}
