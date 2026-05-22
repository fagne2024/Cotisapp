package com.cotisapp.domain.enums;

public enum TypeCompte {
    /** @deprecated Utiliser {@link #EPARGNE_HEBDO} ou {@link #EPARGNE_MOIS}. */
    @Deprecated
    EPARGNE,
    EPARGNE_HEBDO,
    EPARGNE_MOIS,
    DEPENSE,
    SOLIDARITE,
    PENALITE,
    AMENDE,
    /** Intérêts et frais d'emprunt collectés (organisation). */
    INTERET,
    /** Amendes et pénalités collectées (organisation). */
    AMENDES,
    /** Compte membre lié à un modèle personnalisé organisation. */
    CUSTOM,
    CAISSE,
    BANQUE
}
