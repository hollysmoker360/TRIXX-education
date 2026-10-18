package com.schoolsphere.auth.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.InvitationApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.TenantsApi;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Lazy;

@Service
@Transactional
public class InvitationService implements InvitationApi {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantsApi tenantsApi;
    private final PeopleApi peopleApi;
    private final AuditLogService auditLogService;

    public InvitationService(InvitationRepository invitationRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             TenantsApi tenantsApi,
                             @Lazy PeopleApi peopleApi,
                             AuditLogService auditLogService) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantsApi = tenantsApi;
        this.peopleApi = peopleApi;
        this.auditLogService = auditLogService;
    }

    @Override
    public CreatedInvitation createInvitation(String email, String roleCode, UUID schoolId, UUID childStudentId, UUID invitedById) {
        Invitation invitation = new Invitation();
        String token = UUID.randomUUID().toString();
        invitation.setToken(token);
        invitation.setEmail(email.trim().toLowerCase());
        invitation.setRoleCode(roleCode.toLowerCase().trim());
        invitation.setSchoolId(schoolId);
        invitation.setChildStudentId(childStudentId);
        invitation.setInvitedById(invitedById);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setStatus("PENDING");
        invitationRepository.save(invitation);

        String setupUrl = "/accept-invitation?token=" + token;

        auditLogService.record("invitation.created", "account_invitation", invitation.getId(), null, email);

        return new CreatedInvitation(
                invitation.getId(),
                token,
                email,
                roleCode,
                schoolId,
                childStudentId,
                setupUrl
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationDetails getInvitationDetails(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invalid invitation token."));

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus()) || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("invitation_expired", "This invitation token has expired or already been used.");
        }

        String schoolName = null;
        if (invitation.getSchoolId() != null) {
            schoolName = tenantsApi.listMemberships(invitation.getInvitedById()).stream()
                    .filter(m -> m.schoolId().equals(invitation.getSchoolId()))
                    .map(TenantsApi.MembershipSummary::schoolName)
                    .findFirst()
                    .orElse("SchoolSphere Partner School");
        }

        String studentName = null;
        if (invitation.getSchoolId() != null && invitation.getChildStudentId() != null) {
            studentName = peopleApi.findStudent(invitation.getSchoolId(), invitation.getChildStudentId())
                    .map(PeopleApi.StudentRef::fullName)
                    .orElse(null);
        }

        return new InvitationDetails(
                invitation.getToken(),
                invitation.getEmail(),
                invitation.getRoleCode(),
                invitation.getSchoolId(),
                schoolName,
                invitation.getChildStudentId(),
                studentName,
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }

    @Override
    public CompletedInvitation completeInvitation(String token, String displayName, String password, String phone) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("invitation_not_found", "Invalid invitation token."));

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus()) || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("invitation_expired", "This invitation token has expired or already been used.");
        }

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(invitation.getEmail()).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(invitation.getEmail());
            user.setDisplayName(displayName);
            user.setPhone(phone);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setStatus("active");
            userRepository.save(user);
        } else {
            user.setPasswordHash(passwordEncoder.encode(password));
            if (displayName != null && !displayName.isBlank()) {
                user.setDisplayName(displayName);
            }
            if (phone != null && !phone.isBlank()) {
                user.setPhone(phone);
            }
            user.setStatus("active");
            userRepository.save(user);
        }

        if (invitation.getSchoolId() != null) {
            tenantsApi.ensureMembership(invitation.getSchoolId(), user.getId(), invitation.getRoleCode());

            if (invitation.getChildStudentId() != null && "parent".equalsIgnoreCase(invitation.getRoleCode())) {
                PeopleApi.ParentRef parent = peopleApi.parentForUser(invitation.getSchoolId(), user.getId()).orElse(null);
                if (parent == null) {
                    String[] parts = displayName.trim().split("\\s+", 2);
                    String firstName = parts[0];
                    String lastName = parts.length > 1 ? parts[1] : "Parent";
                    parent = peopleApi.createParent(invitation.getSchoolId(), user.getId(), firstName, lastName, phone);
                }
                try {
                    peopleApi.createGuardianLink(
                            invitation.getSchoolId(),
                            parent.id(),
                            invitation.getChildStudentId(),
                            "parent",
                            true,
                            Map.of("attendance", true, "grades", true, "communication", true)
                    );
                } catch (Exception ignored) {
                    // Guardian link already exists or linked
                }
            }
        }

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        auditLogService.record("invitation.completed", "account_invitation", invitation.getId(), null, user.getEmail());

        return new CompletedInvitation(true, "Account setup completed successfully.", user.getEmail(), user.getId());
    }
}
