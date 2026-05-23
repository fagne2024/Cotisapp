package com.cotisapp.controller;

import com.cotisapp.dto.response.DashboardResponse;
import com.cotisapp.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organisations/{orgId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("@orgSecurityService.canAccessOrg(#orgId)")
    public DashboardResponse get(@PathVariable Long orgId) {
        return dashboardService.getDashboard(orgId);
    }
}
