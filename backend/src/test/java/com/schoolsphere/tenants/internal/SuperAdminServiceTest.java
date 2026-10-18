package com.schoolsphere.tenants.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.InvitationApi;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.CurrentPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

class SuperAdminServiceTest {

    private SchoolRepository schoolRepository;
    private UserDirectory userDirectory;
    private InvitationApi invitationApi;
    private AuditLogService auditLogService;
    private JdbcTemplate jdbcTemplate;
    private SuperAdminService superAdminService;

    private UUID superAdminUserId;

    @BeforeEach
    void setUp() {
        schoolRepository = mock(SchoolRepository.class);
        userDirectory = mock(UserDirectory.class);
        invitationApi = mock(InvitationApi.class);
        auditLogService = mock(AuditLogService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        superAdminService = new SuperAdminService(
                schoolRepository,
                userDirectory,
                invitationApi,
                auditLogService,
                jdbcTemplate
        );

        superAdminUserId = UUID.randomUUID();
        when(userDirectory.isPlatformAdmin(superAdminUserId)).thenReturn(true);

        CurrentPrincipal principal = new CurrentPrincipal(
                superAdminUserId, "Platform Admin", "superadmin@schoolsphere.dev",
                null, null, Set.of(), "dev-1"
        );
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, List.of()
        );
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));

        when(schoolRepository.save(any(School.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDashboardMetricsReturnsAggregatePlatformStats() {
        School s1 = new School();
        s1.setStatus("active");
        School s2 = new School();
        s2.setStatus("suspended");

        when(schoolRepository.findAll()).thenReturn(List.of(s1, s2));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class))).thenReturn(150L);

        SuperAdminService.DashboardMetricsDto metrics = superAdminService.getDashboardMetrics();

        assertThat(metrics.totalSchools()).isEqualTo(2);
        assertThat(metrics.activeTenants()).isEqualTo(1);
        assertThat(metrics.suspendedTenants()).isEqualTo(1);
        assertThat(metrics.totalUsers()).isEqualTo(150);
        assertThat(metrics.platformHealth()).isEqualTo("HEALTHY");
    }

    @Test
    void inviteSchoolCreatesPendingTenantAndInvitationToken() {
        when(schoolRepository.existsBySlugAndDeletedAtIsNull("greenfield-academy")).thenReturn(false);
        when(invitationApi.createInvitation(any(), any(), any(), any(), any()))
                .thenAnswer(i -> new InvitationApi.CreatedInvitation(
                        UUID.randomUUID(),
                        "mock-token-123",
                        i.getArgument(0),
                        i.getArgument(1),
                        i.getArgument(2),
                        null,
                        "/accept-invitation?token=mock-token-123"
                ));

        SuperAdminService.SchoolInviteDto result = superAdminService.inviteSchool(
                "Greenfield Academy",
                "greenfield-academy",
                "admin@greenfield.ac.ke"
        );

        assertThat(result.schoolName()).isEqualTo("Greenfield Academy");
        assertThat(result.slug()).isEqualTo("greenfield-academy");
        assertThat(result.adminEmail()).isEqualTo("admin@greenfield.ac.ke");
        assertThat(result.invitationToken()).isEqualTo("mock-token-123");
        assertThat(result.setupUrl()).contains("/accept-invitation?token=");
        verify(auditLogService).record(any(), any(), any(), any(), any());
    }

    @Test
    void inviteSchoolFailsIfSlugAlreadyExists() {
        when(schoolRepository.existsBySlugAndDeletedAtIsNull("existing-school")).thenReturn(true);

        assertThatThrownBy(() -> superAdminService.inviteSchool("Existing School", "existing-school", "admin@school.ac.ke"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateSchoolStatusUpdatesStateAndRecordsAuditLog() {
        UUID schoolId = UUID.randomUUID();
        School school = new School();
        school.setStatus("active");

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));

        SuperAdminService.SchoolSummaryDto summary = superAdminService.updateSchoolStatus(schoolId, "suspended", "Non-payment of subscription");

        assertThat(summary.status()).isEqualTo("suspended");
        verify(auditLogService).record(any(), any(), any(), any(), any());
    }

    @Test
    void regularUserCannotAccessSuperAdminActions() {
        when(userDirectory.isPlatformAdmin(superAdminUserId)).thenReturn(false);

        assertThatThrownBy(() -> superAdminService.getDashboardMetrics())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("super admin privileges are required");
    }
}
