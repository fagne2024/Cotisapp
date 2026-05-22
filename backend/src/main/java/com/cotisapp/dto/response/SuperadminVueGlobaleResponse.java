package com.cotisapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SuperadminVueGlobaleResponse {
    private SuperadminKpiResponse kpi;
    private List<OrganisationResumeResponse> organisations;
    private List<CotisationOrgChartResponse> cotisationsParOrganisation;
    private List<SuperadminActiviteResponse> activiteRecente;
}
