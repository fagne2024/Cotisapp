package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TelephoneLookupRequest {
    @NotBlank
    @Size(max = 30)
    private String telephone;
}
