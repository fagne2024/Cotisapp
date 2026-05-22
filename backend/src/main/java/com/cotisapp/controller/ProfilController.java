package com.cotisapp.controller;

import com.cotisapp.dto.request.ChangeMotDePasseRequest;
import com.cotisapp.dto.request.UpdateProfilRequest;
import com.cotisapp.dto.response.ProfilResponse;
import com.cotisapp.service.ProfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class ProfilController {

    private final ProfilService profilService;

    @GetMapping
    public ProfilResponse profil() {
        return profilService.chargerProfilCourant();
    }

    @GetMapping("/activite")
    public java.util.List<com.cotisapp.dto.response.ProfilActiviteResponse> activite() {
        return profilService.activiteRecente();
    }

    @PatchMapping
    public ProfilResponse mettreAJour(@Valid @RequestBody UpdateProfilRequest request) {
        return profilService.mettreAJour(request);
    }

    @PatchMapping("/mot-de-passe")
    public void changerMotDePasse(@Valid @RequestBody ChangeMotDePasseRequest request) {
        profilService.changerMotDePasse(request);
    }
}
