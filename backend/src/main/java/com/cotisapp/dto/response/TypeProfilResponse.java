package com.cotisapp.dto.response;

import com.cotisapp.domain.enums.CanalConnexion;
import com.cotisapp.domain.enums.PosteMembre;
import com.cotisapp.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TypeProfilResponse {
    private Long id;
    private Long organisationId;
    private String code;
    private String libelle;
    private Role role;
    private PosteMembre posteMembre;
    private CanalConnexion canalConnexion;
    private boolean actif;
    private int ordre;
    /** Type créé par l'application (MEMBRE, SG…) — suppression interdite. */
    private boolean systeme;
}
