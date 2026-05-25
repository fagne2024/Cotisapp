package com.cotisapp.service.cloture;



import com.cotisapp.domain.enums.TypeCompte;

import com.cotisapp.domain.enums.TypeOperation;



/**

 * Poste de montant à partager à la clôture.

 * {@code builtIn} : INTERETS, PENALITES, AMENDES — calcul automatique.

 * Sinon : somme des opérations du {@code typeOperation} sur l'exercice.

 */

public record PostePartageClotureItem(

        String code,

        String libelle,

        boolean actif,

        boolean builtIn,

        TypeCompte compteMembre,

        TypeCompte compteSourceOrg,

        TypeOperation typeOperation,

        /** 1 ou 2 lorsque le mode d'agrégation est {@code GROUPES}. */

        Integer groupePartage,

        /**

         * En mode {@code ADDITIONNER} : ce poste entre dans le pool commun additionné

         * (les autres postes actifs restent répartis à part).

         */

        boolean inclureDansPoolAdditionne,

        /**

         * En mode {@code PRORATA} global : si true, ce poste utilise parts / % ;

         * sinon répartition équitable pour ce poste uniquement.

         */

        boolean appliquerProrata) {



    public PostePartageClotureItem(

            String code,

            String libelle,

            boolean actif,

            boolean builtIn,

            TypeCompte compteMembre,

            TypeCompte compteSourceOrg,

            TypeOperation typeOperation) {

        this(code, libelle, actif, builtIn, compteMembre, compteSourceOrg, typeOperation, null, false, true);

    }



    public PostePartageClotureItem(

            String code,

            String libelle,

            boolean actif,

            boolean builtIn,

            TypeCompte compteMembre,

            TypeCompte compteSourceOrg,

            TypeOperation typeOperation,

            Integer groupePartage,

            boolean inclureDansPoolAdditionne) {

        this(

                code,

                libelle,

                actif,

                builtIn,

                compteMembre,

                compteSourceOrg,

                typeOperation,

                groupePartage,

                inclureDansPoolAdditionne,

                true);

    }



    public static PostePartageClotureItem interetsDefaut() {

        return new PostePartageClotureItem(

                "INTERETS",

                "Intérêts / frais d'emprunt collectés",

                true,

                true,

                TypeCompte.INTERET,

                TypeCompte.INTERET,

                null,

                1,

                true,

                true);

    }



    public static PostePartageClotureItem penalitesDefaut() {

        return new PostePartageClotureItem(

                "PENALITES",

                "Pénalités de retard (remboursements)",

                true,

                true,

                TypeCompte.PENALITE,

                TypeCompte.CAISSE,

                null,

                2,

                true,

                true);

    }



    public static PostePartageClotureItem amendesDefaut() {

        return new PostePartageClotureItem(

                "AMENDES",

                "Amendes sur cotisations",

                true,

                true,

                TypeCompte.AMENDE,

                TypeCompte.CAISSE,

                null,

                2,

                true,

                true);

    }



    /** Valeur par défaut si le champ est absent du JSON en base (anciennes configs). */

    public PostePartageClotureItem avecDefautProrata() {

        return appliquerProrata ? this : new PostePartageClotureItem(

                code,

                libelle,

                actif,

                builtIn,

                compteMembre,

                compteSourceOrg,

                typeOperation,

                groupePartage,

                inclureDansPoolAdditionne,

                true);

    }

}


