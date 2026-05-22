package com.cotisapp.service;

import com.cotisapp.domain.catalogue.ActionDroitCatalogue;
import com.cotisapp.domain.enums.NiveauDroit;
import com.cotisapp.domain.enums.Role;
import com.cotisapp.dto.response.MesDroitsResponse;
import com.cotisapp.security.OrgSecurityService;
import com.cotisapp.security.OrganisationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MesDroitsService {

    private final OrgSecurityService orgSecurityService;

    @Transactional(readOnly = true)
    public MesDroitsResponse chargerPourOrganisation(Long orgId) {
        Map<String, NiveauDroit> actions = new LinkedHashMap<>();
        Role role = OrganisationContext.getRole();
        for (ActionDroitCatalogue.ActionDef def : ActionDroitCatalogue.toutes()) {
            String code = def.code();
            boolean ok = orgSecurityService.peutActionOrg(orgId, code);
            actions.put(code, ok ? (role == Role.MEMBRE ? NiveauDroit.LIM : NiveauDroit.LIM) : NiveauDroit.NO);
        }
        return MesDroitsResponse.builder()
                .peutGestion(orgSecurityService.peutGestionOrg(orgId))
                .actions(actions)
                .build();
    }
}
