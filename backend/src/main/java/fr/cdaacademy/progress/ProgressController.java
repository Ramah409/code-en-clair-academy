package fr.cdaacademy.progress;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import fr.cdaacademy.user.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Progression")
public class ProgressController {

    private final DashboardService dashboard;

    public ProgressController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Accueil : reprise, objectif du jour, série, parcours, badges récents")
    public DashboardService.Dashboard dashboard() {
        return dashboard.dashboard(CurrentUser.id(), CurrentUser.get().role() == RoleCode.ADMIN);
    }

    @GetMapping("/progress")
    @Operation(summary = "Progression détaillée : activité, badges, totaux, parcours")
    public DashboardService.Progress progress() {
        return dashboard.progress(CurrentUser.id(), CurrentUser.get().role() == RoleCode.ADMIN);
    }
}
