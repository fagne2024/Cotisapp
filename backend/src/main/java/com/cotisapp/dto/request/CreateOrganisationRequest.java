package com.cotisapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateOrganisationRequest {
    @NotBlank
    private String code;
    @NotBlank
    private String nom;
    private String description;
    private ComptesOrganisationSelection comptes = new ComptesOrganisationSelection();
    @Valid
    private List<CreateCompteModeleMembreRequest> modelesComptePersonnalises = new ArrayList<>();

    @Valid
    @NotNull(message = "L'administrateur GIE est obligatoire")
    private AdminGieCreationRequest administrateurGie;
}
