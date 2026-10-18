package com.schoolsphere.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.InvitationApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.TenantsApi;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class InvitationServiceTest {

    private InvitationRepository invitationRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TenantsApi tenantsApi;
    private PeopleApi peopleApi;
    private AuditLogService auditLogService;
    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationRepository = mock(InvitationRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        tenantsApi = mock(TenantsApi.class);
        peopleApi = mock(PeopleApi.class);
        auditLogService = mock(AuditLogService.class);

        invitationService = new InvitationService(
                invitationRepository,
                userRepository,
                passwordEncoder,
                tenantsApi,
                peopleApi,
                auditLogService
        );

        when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createInvitationGeneratesTokenAndUrl() {
        UUID schoolId = UUID.randomUUID();
        UUID childStudentId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();

        InvitationApi.CreatedInvitation created = invitationService.createInvitation(
                "parent@example.com",
                "parent",
                schoolId,
                childStudentId,
                inviterId
        );

        assertThat(created.email()).isEqualTo("parent@example.com");
        assertThat(created.roleCode()).isEqualTo("parent");
        assertThat(created.schoolId()).isEqualTo(schoolId);
        assertThat(created.childStudentId()).isEqualTo(childStudentId);
        assertThat(created.token()).isNotBlank();
        assertThat(created.setupUrl()).contains("/accept-invitation?token=");
    }

    @Test
    void getInvitationDetailsReturnsPendingTokenInfo() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Invitation invitation = new Invitation();
        invitation.setToken("valid-token");
        invitation.setEmail("parent@example.com");
        invitation.setRoleCode("parent");
        invitation.setSchoolId(schoolId);
        invitation.setChildStudentId(studentId);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));

        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(peopleApi.findStudent(schoolId, studentId))
                .thenReturn(Optional.of(new PeopleApi.StudentRef(studentId, null, "ADM-001", "Faith", "Njeri", "active")));

        InvitationApi.InvitationDetails details = invitationService.getInvitationDetails("valid-token");

        assertThat(details.email()).isEqualTo("parent@example.com");
        assertThat(details.studentName()).isEqualTo("Faith Njeri");
        assertThat(details.status()).isEqualTo("PENDING");
    }

    @Test
    void getInvitationDetailsRejectsExpiredToken() {
        Invitation expired = new Invitation();
        expired.setToken("expired-token");
        expired.setStatus("PENDING");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(invitationRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> invitationService.getInvitationDetails("expired-token"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void completeInvitationCreatesUserMembershipAndLinksParentToChild() {
        UUID schoolId = UUID.randomUUID();
        UUID childStudentId = UUID.randomUUID();
        UUID parentUserId = UUID.randomUUID();

        Invitation invitation = new Invitation();
        invitation.setToken("token-123");
        invitation.setEmail("alice@example.com");
        invitation.setRoleCode("parent");
        invitation.setSchoolId(schoolId);
        invitation.setChildStudentId(childStudentId);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        when(invitationRepository.findByToken("token-123")).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("alice@example.com")).thenReturn(Optional.empty());

        when(peopleApi.parentForUser(eq(schoolId), any())).thenReturn(Optional.empty());
        when(peopleApi.createParent(eq(schoolId), any(), eq("Alice"), eq("Njeri"), eq("0712345678")))
                .thenReturn(new PeopleApi.ParentRef(UUID.randomUUID(), parentUserId, "Alice", "Njeri"));

        InvitationApi.CompletedInvitation result = invitationService.completeInvitation(
                "token-123",
                "Alice Njeri",
                "Password123!",
                "0712345678"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(invitation.getStatus()).isEqualTo("ACCEPTED");
        verify(tenantsApi).ensureMembership(eq(schoolId), any(), eq("parent"));
        verify(peopleApi).createGuardianLink(eq(schoolId), any(), eq(childStudentId), eq("parent"), eq(true), any());
    }
}
