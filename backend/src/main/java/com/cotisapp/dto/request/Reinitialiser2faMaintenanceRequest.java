package com.cotisapp.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Reinitialiser2faMaintenanceRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String cle;
}
