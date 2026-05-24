package com.cotisapp.controller;

import com.cotisapp.dto.request.ChangeMotDePasseInitialRequest;
import com.cotisapp.dto.request.LoginRequest;
import com.cotisapp.dto.request.RefreshTokenRequest;
import com.cotisapp.dto.request.TelephoneLookupRequest;
import com.cotisapp.dto.request.VerifyTwoFactorRequest;
import com.cotisapp.dto.response.AuthResponse;
import com.cotisapp.dto.response.CompteMembreLoginDto;
import com.cotisapp.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest httpRequest) {
        authService.deconnexion(httpRequest);
        return Map.of("message", "Déconnexion enregistrée");
    }

    @PostMapping("/verify-2fa")
    public AuthResponse verifyTwoFactor(
            @Valid @RequestBody VerifyTwoFactorRequest request, HttpServletRequest httpRequest) {
        return authService.verifyTwoFactor(request, httpRequest);
    }

    /** Liste les fiches membre accessibles pour un numéro (connexion multi-comptes). */
    @PostMapping("/comptes-membre")
    public List<CompteMembreLoginDto> listerComptesMembre(@Valid @RequestBody TelephoneLookupRequest request) {
        return authService.listerComptesMembreParTelephone(request.getTelephone());
    }

    @PostMapping("/changer-mot-de-passe-initial")
    public AuthResponse changerMotDePasseInitial(@Valid @RequestBody ChangeMotDePasseInitialRequest request) {
        return authService.changerMotDePasseInitial(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.rafraichirToken(request.getRefreshToken());
    }
}
