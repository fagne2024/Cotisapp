package com.cotisapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RembourserRequest {
    private Long echeanceId;
    @NotNull @DecimalMin("0.01")
    private BigDecimal montant;
    @DecimalMin("0")
    private BigDecimal montantCapital;
    @DecimalMin("0")
    private BigDecimal montantFrais;
    /** Pénalité de retard (calculée côté serveur si absente). */
    @DecimalMin("0")
    private BigDecimal montantPenalite;
    /** Si false, aucune pénalité n'est appliquée même en cas de retard. */
    private Boolean appliquerPenalite;
    @NotNull
    private LocalDate datePaiement;
    /** ESPECES, WAVE, ORANGE_MONEY (défaut ESPECES). */
    @Size(max = 30)
    private String modePaiement;
    /** N° transaction Wave / Orange Money (optionnel). */
    @Size(max = 100)
    private String referencePaiement;
    @Size(max = 500)
    private String observation;
}
