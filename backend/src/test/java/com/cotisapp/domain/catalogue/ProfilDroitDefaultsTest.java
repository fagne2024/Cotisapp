package com.cotisapp.domain.catalogue;

import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.PosteMembre;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilDroitDefaultsTest {

    @Test
    void superviseur_sans_saisie_operations() {
        Map<String, NiveauDroit> droits = ProfilDroitDefaults.superviseur();
        assertThat(droits.get("OP_COTISATION")).isEqualTo(NiveauDroit.NO);
        assertThat(droits.get("OP_EMPRUNT")).isEqualTo(NiveauDroit.NO);
        assertThat(droits.get("RAPPORT_COMPLET")).isEqualTo(NiveauDroit.LIM);
        assertThat(droits.get("MEMBRE_LISTER")).isEqualTo(NiveauDroit.LIM);
    }

    @Test
    void secretaire_general_peut_gerer_cotisations_et_emprunts() {
        Map<String, NiveauDroit> droits = ProfilDroitDefaults.secretaireGeneral();
        assertThat(droits.get("OP_COTISATION")).isEqualTo(NiveauDroit.LIM);
        assertThat(droits.get("OP_EMPRUNT")).isEqualTo(NiveauDroit.LIM);
        assertThat(droits.get("OP_DEPENSE")).isEqualTo(NiveauDroit.NO);
        assertThat(droits.get("ADMIN_UTILISATEURS")).isEqualTo(NiveauDroit.NO);
    }

    @Test
    void tresorier_peut_depenses_et_banque() {
        Map<String, NiveauDroit> droits = ProfilDroitDefaults.tresorier();
        assertThat(droits.get("OP_DEPENSE")).isEqualTo(NiveauDroit.LIM);
        assertThat(droits.get("OP_BANQUE")).isEqualTo(NiveauDroit.LIM);
        assertThat(droits.get("MEMBRE_GERER")).isEqualTo(NiveauDroit.NO);
    }

    @Test
    void sga_moins_de_droits_que_sg() {
        Map<String, NiveauDroit> sg = ProfilDroitDefaults.secretaireGeneral();
        Map<String, NiveauDroit> sga = ProfilDroitDefaults.secretaireGeneralAdjoint();
        assertThat(sga.get("OP_ANNULER")).isEqualTo(NiveauDroit.NO);
        assertThat(sg.get("OP_ANNULER")).isEqualTo(NiveauDroit.LIM);
        assertThat(sga.get("MEMBRE_CHANGER_POSTE")).isEqualTo(NiveauDroit.NO);
    }

    @Test
    void pourProfil_resout_par_poste() {
        assertThat(ProfilDroitDefaults.pourProfil(
                        com.cotisapp.domain.enums.Role.MEMBRE, PosteMembre.SUPERVISEUR)
                .get("OP_COTISATION"))
                .isEqualTo(NiveauDroit.NO);
    }
}
