package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TwoFactorSetupResponse {
    private String secret;
    private String otpAuthUrl;
    private String qrCodeDataUrl;
    private String issuer;
}
