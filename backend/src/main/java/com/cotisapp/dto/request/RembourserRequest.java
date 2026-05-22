package com.cotisapp.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RembourserRequest {
    private Long echeanceId;
    private BigDecimal montant;
    private BigDecimal montantCapital;
    private BigDecimal montantFrais;
    /** Pénalité de retard (calculée côté serveur si absente). */
    private BigDecimal montantPenalite;
    /** Si false, aucune pénalité n'est appliquée même en cas de retard. */
    private Boolean appliquerPenalite;
    @NotNull
    private LocalDate datePaiement;
    /** ESPECES, WAVE, ORANGE_MONEY (défaut ESPECES). */
    private String modePaiement;
    /** N° transaction Wave / Orange Money (optionnel). */
    private String referencePaiement;
    private String observation;
}
