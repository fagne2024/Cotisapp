package com.cotisapp.controller;

import com.cotisapp.dto.request.TwoFactorConfirmRequest;
import com.cotisapp.dto.request.TwoFactorDisableRequest;
import com.cotisapp.dto.response.TwoFactorSetupResponse;
import com.cotisapp.dto.response.TwoFactorStatusResponse;
import com.cotisapp.service.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @GetMapping("/status")
    public TwoFactorStatusResponse status() {
        return twoFactorService.status();
    }

    @PostMapping("/setup")
    public TwoFactorSetupResponse setup() {
        return twoFactorService.demarrerConfiguration();
    }

    @PostMapping("/confirm")
    public TwoFactorStatusResponse confirm(@Valid @RequestBody TwoFactorConfirmRequest request) {
        return twoFactorService.confirmerConfiguration(request);
    }

    @DeleteMapping("/setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void annulerSetup() {
        twoFactorService.annulerConfiguration();
    }

    @PostMapping("/disable")
    public TwoFactorStatusResponse disable(@Valid @RequestBody TwoFactorDisableRequest request) {
        return twoFactorService.desactiver(request);
    }
}
