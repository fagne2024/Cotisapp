package com.cotisapp.domain.enums;

/**
 * Comment combiner les postes (intérêts, pénalités, etc.) avant répartition.
 */
public enum ModeAgregationPostesCloture {
    /** Chaque poste actif est réparti séparément. */
    SEPARER,
    /**
     * Les postes cochés « additionner » sont sommés puis répartis ensemble ;
     * les autres postes actifs restent répartis séparément.
     */
    ADDITIONNER,
    /** Les postes sont regroupés par {@code groupePartage} (1 ou 2) puis répartis par groupe. */
    GROUPES
}
