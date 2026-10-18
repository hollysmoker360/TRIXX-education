package com.schoolsphere.tenants.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.InvitationApi;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SuperAdminService {

    private final SchoolRepository schoolRepository;
    private final UserDirectory userDirectory;
    private final InvitationApi invitationApi;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    public SuperAdminService(SchoolRepository schoolRepository,
                             UserDirectory userDirectory,
                             InvitationApi invitationApi,
                             AuditLogService auditLogService,
                             JdbcTemplate jdbcTemplate) {
        this.schoolRepository = schoolRepository;
        this.userDirectory = userDirectory;
        this.invitationApi = invitationApi;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private void requireSuperAdmin() {
        UUID userId = SecurityUtils.requireUserId();
        if (!userDirectory.isPlatformAdmin(userId)) {
            throw ApiException.forbidden("super_admin_required", "Platform super admin privileges are required for this action.");
        }
    }

    @Transactional(readOnly = true)
    public DashboardMetricsDto getDashboardMetrics() {
        requireSuperAdmin();

        List<School> allSchools = schoolRepository.findAll();
        long totalSchools = allSchools.size();
        long activeTenants = allSchools.stream().filter(s -> "active".equalsIgnoreCase(s.getStatus())).count();
        long suspendedTenants = allSchools.stream().filter(s -> "suspended".equalsIgnoreCase(s.getStatus())).count();
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE deleted_at IS NULL", Long.class);
        long totalUsers = count != null ? count : 0L;

        List<SchoolSummaryDto> schoolDtos = allSchools.stream()
                .map(s -> new SchoolSummaryDto(
                        s.getId(),
                        s.getName(),
                        s.getSlug(),
                        s.getStatus(),
                        s.getCreatedAt() != null ? s.getCreatedAt().toString() : Instant.now().toString()))
                .toList();

        return new DashboardMetricsDto(
                totalSchools,
                activeTenants,
                suspendedTenants,
                totalUsers,
                "HEALTHY",
                schoolDtos);
    }

    public SchoolInviteDto inviteSchool(String name, String slug, String adminEmail) {
        requireSuperAdmin();

        if (schoolRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw ApiException.badRequest("slug_exists", "A school with this slug already exists.");
        }

        School school = new School();
        school.setName(name);
        school.setSlug(slug.toLowerCase().trim());
        school.setStatus("PENDING");
        schoolRepository.save(school);

        InvitationApi.CreatedInvitation created = invitationApi.createInvitation(
                adminEmail,
                "admin",
                school.getId(),
                null,
                SecurityUtils.requireUserId()
        );

        auditLogService.record("SCHOOL_INVITED", "school", school.getId(), null, school.getName());

        return new SchoolInviteDto(
                school.getId(),
                school.getName(),
                school.getSlug(),
                adminEmail,
                created.token(),
                created.setupUrl());
    }

    public SchoolSummaryDto updateSchoolStatus(UUID schoolId, String status, String reason) {
        requireSuperAdmin();

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> ApiException.notFound("school_not_found", "School not found."));

        String oldStatus = school.getStatus();
        school.setStatus(status.toLowerCase().trim());
        schoolRepository.save(school);

        auditLogService.record("SCHOOL_STATUS_UPDATED", "school", schoolId, oldStatus, status + " (" + reason + ")");

        return new SchoolSummaryDto(
                school.getId(),
                school.getName(),
                school.getSlug(),
                school.getStatus(),
                school.getCreatedAt() != null ? school.getCreatedAt().toString() : Instant.now().toString());
    }

    public record DashboardMetricsDto(
            long totalSchools,
            long activeTenants,
            long suspendedTenants,
            long totalUsers,
            String platformHealth,
            List<SchoolSummaryDto> schools) {
    }

    public record SchoolSummaryDto(
            UUID id,
            String name,
            String slug,
            String status,
            String createdAt) {
    }

    public record SchoolInviteDto(
            UUID schoolId,
            String schoolName,
            String slug,
            String adminEmail,
            String invitationToken,
            String setupUrl) {
    }
}
