package com.schoolsphere.tenants.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.tenants.api.PermissionCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Tenant-scoped school administration: settings, memberships, and custom roles (spec 8.2, 8.3).
 */
@Service
@TenantScoped
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolRoleRepository roleRepository;
    private final SchoolMembershipRepository membershipRepository;
    private final UserDirectory userDirectory;
    private final AuditLogService auditLogService;

    public SchoolService(SchoolRepository schoolRepository,
                         SchoolRoleRepository roleRepository,
                         SchoolMembershipRepository membershipRepository,
                         UserDirectory userDirectory,
                         AuditLogService auditLogService) {
        this.schoolRepository = schoolRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.userDirectory = userDirectory;
        this.auditLogService = auditLogService;
    }

    public SchoolView currentSchool() {
        UUID schoolId = TenantContext.schoolId();
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> ApiException.notFound("school_not_found", "School not found."));
        return toView(school);
    }

    public SchoolView updateSchool(Map<String, Object> patch) {
        UUID schoolId = TenantContext.schoolId();
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> ApiException.notFound("school_not_found", "School not found."));
        SchoolView before = toView(school);
        if (patch.containsKey("name") && patch.get("name") != null) {
            school.setName(String.valueOf(patch.get("name")));
        }
        if (patch.containsKey("motto")) {
            school.setMotto(patch.get("motto") == null ? null : String.valueOf(patch.get("motto")));
        }
        if (patch.containsKey("timezone") && patch.get("timezone") != null) {
            school.setTimezone(String.valueOf(patch.get("timezone")));
        }
        if (patch.containsKey("defaultLocale") && patch.get("defaultLocale") != null) {
            school.setDefaultLocale(String.valueOf(patch.get("defaultLocale")));
        }
        school = schoolRepository.save(school);
        SchoolView after = toView(school);
        auditLogService.record("school.settings.changed", "school", schoolId, before, after);
        return after;
    }

    public List<MemberView> listMemberships() {
        UUID schoolId = TenantContext.schoolId();
        List<SchoolMembership> memberships = membershipRepository.findBySchoolIdAndDeletedAtIsNull(schoolId);
        Map<UUID, UserDirectory.UserRef> users = userDirectory
                .findAllByIdIn(memberships.stream().map(SchoolMembership::getUserId).toList())
                .stream().collect(Collectors.toMap(UserDirectory.UserRef::id, Function.identity()));
        List<MemberView> result = new ArrayList<>();
        for (SchoolMembership m : memberships) {
            UserDirectory.UserRef user = users.get(m.getUserId());
            result.add(new MemberView(m.getId(), m.getUserId(),
                    user == null ? "(unknown)" : user.displayName(),
                    user == null ? null : user.email(),
                    m.getStatus(), m.getTitle(),
                    m.getRoles().stream().map(SchoolRole::getCode).sorted().toList()));
        }
        result.sort(Comparator.comparing(MemberView::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public InviteResult inviteMember(String email, String displayName, List<String> roleCodes, String title) {
        UUID schoolId = TenantContext.schoolId();
        UserDirectory.UserRef user = userDirectory.findByEmail(email).orElse(null);
        String temporaryPassword = null;
        if (user == null) {
            UserDirectory.CreatedUser created = userDirectory.createUserWithTemporaryPassword(email,
                    displayName == null || displayName.isBlank() ? email : displayName, null);
            user = created.user();
            temporaryPassword = created.temporaryPassword();
        }
        if (membershipRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, user.id()).isPresent()) {
            throw ApiException.conflict("already_member", "This user is already a member of the school.");
        }
        SchoolMembership membership = new SchoolMembership();
        membership.setSchoolId(schoolId);
        membership.setUserId(user.id());
        membership.setTitle(title);
        membership.getRoles().addAll(resolveRoles(roleCodes));
        membershipRepository.save(membership);
        auditLogService.record("membership.invited", "school_membership", membership.getId(), null,
                Map.of("userId", user.id(), "roles", roleCodes));
        return new InviteResult(membership.getId(), user, roleCodes, temporaryPassword);
    }

    public MemberView setMembershipStatus(UUID membershipId, String status) {
        UUID schoolId = TenantContext.schoolId();
        if (!List.of("active", "suspended", "revoked").contains(status)) {
            throw ApiException.badRequest("invalid_status", "Status must be active, suspended, or revoked.");
        }
        SchoolMembership membership = membershipRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, membershipId)
                .orElseThrow(() -> ApiException.notFound("membership_not_found", "Membership not found."));
        if (membership.getUserId().equals(TenantContext.actorUserId()) && !"active".equals(status)) {
            throw ApiException.badRequest("cannot_suspend_self", "You cannot suspend or revoke your own membership.");
        }
        String before = membership.getStatus();
        membership.setStatus(status);
        membership.bumpTokenVersion(); // immediate invalidation of affected sessions (spec 8.1)
        membershipRepository.save(membership);
        auditLogService.record("membership.status.changed", "school_membership", membershipId,
                Map.of("status", before), Map.of("status", status));
        return toMemberView(membership);
    }

    public MemberView setMembershipRoles(UUID membershipId, List<String> roleCodes) {
        UUID schoolId = TenantContext.schoolId();
        SchoolMembership membership = membershipRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, membershipId)
                .orElseThrow(() -> ApiException.notFound("membership_not_found", "Membership not found."));
        List<String> before = membership.getRoles().stream().map(SchoolRole::getCode).sorted().toList();
        membership.getRoles().clear();
        membership.getRoles().addAll(resolveRoles(roleCodes));
        membershipRepository.save(membership);
        auditLogService.record("membership.roles.changed", "school_membership", membershipId, before, roleCodes);
        return toMemberView(membership);
    }

    public List<RoleView> listRoles() {
        return roleRepository.findBySchoolIdAndDeletedAtIsNull(TenantContext.schoolId()).stream()
                .sorted(Comparator.comparing(SchoolRole::getCode))
                .map(this::toRoleView)
                .toList();
    }

    public RoleView createRole(String code, String name, Set<String> permissions) {
        UUID schoolId = TenantContext.schoolId();
        String normalizedCode = code.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        if (roleRepository.findBySchoolIdAndCodeAndDeletedAtIsNull(schoolId, normalizedCode).isPresent()) {
            throw ApiException.conflict("role_exists", "A role with this code already exists.");
        }
        validatePermissions(permissions);
        SchoolRole role = new SchoolRole();
        role.setSchoolId(schoolId);
        role.setCode(normalizedCode);
        role.setName(name);
        role.setSystem(false);
        role.setPermissions(new LinkedHashSet<>(permissions));
        roleRepository.save(role);
        auditLogService.record("role.created", "school_role", role.getId(), null, toRoleView(role));
        return toRoleView(role);
    }

    public RoleView updateRole(UUID roleId, String name, Set<String> permissions) {
        UUID schoolId = TenantContext.schoolId();
        SchoolRole role = roleRepository.findById(roleId)
                .filter(r -> r.getSchoolId().equals(schoolId) && r.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("role_not_found", "Role not found."));
        RoleView before = toRoleView(role);
        if (name != null && !name.isBlank()) {
            role.setName(name);
        }
        if (permissions != null) {
            validatePermissions(permissions);
            role.setPermissions(new LinkedHashSet<>(permissions));
        }
        roleRepository.save(role);
        auditLogService.record("role.updated", "school_role", roleId, before, toRoleView(role));
        return toRoleView(role);
    }

    private void validatePermissions(Set<String> permissions) {
        for (String permission : permissions) {
            if (!PermissionCatalog.ALL_PERMISSIONS.contains(permission)) {
                throw ApiException.badRequest("unknown_permission", "Unknown permission: " + permission);
            }
        }
    }

    private List<SchoolRole> resolveRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw ApiException.badRequest("roles_required", "At least one role is required.");
        }
        List<SchoolRole> roles = new ArrayList<>();
        for (String code : roleCodes) {
            roles.add(roleRepository.findBySchoolIdAndCodeAndDeletedAtIsNull(TenantContext.schoolId(), code)
                    .orElseThrow(() -> ApiException.badRequest("unknown_role", "Unknown role: " + code)));
        }
        return roles;
    }

    private MemberView toMemberView(SchoolMembership m) {
        UserDirectory.UserRef user = userDirectory.findById(m.getUserId()).orElse(null);
        return new MemberView(m.getId(), m.getUserId(),
                user == null ? "(unknown)" : user.displayName(),
                user == null ? null : user.email(),
                m.getStatus(), m.getTitle(),
                m.getRoles().stream().map(SchoolRole::getCode).sorted().toList());
    }

    private SchoolView toView(School school) {
        return new SchoolView(school.getId(), school.getName(), school.getSlug(), school.getMotto(),
                school.getTimezone(), school.getDefaultLocale(), school.getCurrency(), school.getStatus());
    }

    private RoleView toRoleView(SchoolRole role) {
        return new RoleView(role.getId(), role.getCode(), role.getName(), role.isSystem(),
                role.getPermissions().stream().sorted().toList());
    }

    public record SchoolView(UUID id, String name, String slug, String motto, String timezone,
                             String defaultLocale, String currency, String status) {
    }

    public record MemberView(UUID membershipId, UUID userId, String displayName, String email,
                             String status, String title, List<String> roles) {
    }

    public record RoleView(UUID id, String code, String name, boolean system, List<String> permissions) {
    }

    public record InviteResult(UUID membershipId, UserDirectory.UserRef user, List<String> roles,
                               String temporaryPassword) {
    }
}
