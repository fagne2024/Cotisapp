package com.cotisapp.domain.catalogue;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionDroitCatalogueTest {

    @Test
    void toutes_ontDesCodesUniquesEtAuMoinsUneSection() {
        List<ActionDroitCatalogue.ActionDef> actions = ActionDroitCatalogue.toutes();
        Set<String> codes = new HashSet<>();
        boolean sectionVue = false;
        for (ActionDroitCatalogue.ActionDef a : actions) {
            assertTrue(codes.add(a.code()), "Code dupliqué : " + a.code());
            if (a.section() != null) {
                sectionVue = true;
            }
        }
        assertEquals(26, actions.size());
        assertTrue(sectionVue);
    }
}
