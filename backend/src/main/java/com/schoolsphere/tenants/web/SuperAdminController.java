package com.schoolsphere.tenants.web;

import com.schoolsphere.tenants.internal.SuperAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/super-admin")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    public SuperAdminController(SuperAdminService superAdminService) {
        this.superAdminService = superAdminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDashboardResponse> getDashboard() {
        SuperAdminService.DashboardMetricsDto metrics = superAdminService.getDashboardMetrics();
        List<SchoolSummaryResponse> schools = metrics.schools().stream()
                .map(s -> new SchoolSummaryResponse(s.id(), s.name(), s.slug(), s.status(), s.createdAt()))
                .toList();

        return ResponseEntity.ok(new SuperAdminDashboardResponse(
                metrics.totalSchools(),
                metrics.activeTenants(),
                metrics.suspendedTenants(),
                metrics.totalUsers(),
                metrics.platformHealth(),
                schools
        ));
    }

    @PostMapping("/schools/invite")
    public ResponseEntity<InviteSchoolResponse> inviteSchool(@Valid @RequestBody InviteSchoolRequest request) {
        SuperAdminService.SchoolInviteDto invite = superAdminService.inviteSchool(
                request.name(),
                request.slug(),
                request.adminEmail()
        );

        return ResponseEntity.ok(new InviteSchoolResponse(
                invite.schoolId(),
                invite.schoolName(),
                invite.slug(),
                invite.adminEmail(),
                invite.invitationToken(),
                invite.setupUrl()
        ));
    }

    @PutMapping("/schools/{id}/status")
    public ResponseEntity<SchoolSummaryResponse> updateSchoolStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSchoolStatusRequest request) {
        SuperAdminService.SchoolSummaryDto summary = superAdminService.updateSchoolStatus(
                id,
                request.status(),
                request.reason()
        );

        return ResponseEntity.ok(new SchoolSummaryResponse(
                summary.id(),
                summary.name(),
                summary.slug(),
                summary.status(),
                summary.createdAt()
        ));
    }

    public record InviteSchoolRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String slug,
            @NotBlank @Email String adminEmail) {
    }

    public record InviteSchoolResponse(
            UUID schoolId,
            String schoolName,
            String slug,
            String adminEmail,
            String invitationToken,
            String setupUrl) {
    }

    public record UpdateSchoolStatusRequest(
            @NotBlank String status,
            @NotBlank String reason) {
    }

    public record SchoolSummaryResponse(
            UUID id,
            String name,
            String slug,
            String status,
            String createdAt) {
    }

    public record SuperAdminDashboardResponse(
            long totalSchools,
            long activeTenants,
            long suspendedTenants,
            long totalUsers,
            String platformHealth,
            List<SchoolSummaryResponse> schools) {
    }
}
