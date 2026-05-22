package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TelephoneLookupRequest {
    @NotBlank
    private String telephone;
}
