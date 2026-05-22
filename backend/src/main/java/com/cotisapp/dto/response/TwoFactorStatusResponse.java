package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TwoFactorStatusResponse {
    private boolean enabled;
    private boolean pendingSetup;
}
