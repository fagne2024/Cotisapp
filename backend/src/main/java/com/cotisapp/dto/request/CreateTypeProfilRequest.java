package com.cotisapp.dto.request;

import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTypeProfilRequest {
    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 120)
    private String libelle;

    @NotNull
    private Role role;

    private PosteMembre posteMembre;
    private CanalConnexion canalConnexion;
    private Boolean actif;
    private Integer ordre;
}
