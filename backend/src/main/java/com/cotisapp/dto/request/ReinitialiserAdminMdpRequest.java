package com.cotisapp.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReinitialiserAdminMdpRequest {

    /** Vide = mot de passe par défaut Admin@2026 */
    @Size(min = 8, max = 100)
    private String motDePasse;

    /** Si true, l'admin devra changer son mot de passe à la prochaine connexion. */
    private Boolean forcerChangement = false;
}
