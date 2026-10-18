package com.schoolsphere.tenants.internal;

import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.tenants.api.PermissionCatalog;
import com.schoolsphere.tenants.api.TenantsApi;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantsApiImpl implements TenantsApi {

    private final SchoolRepository schoolRepository;
    private final SchoolRoleRepository roleRepository;
    private final SchoolMembershipRepository membershipRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TenantsApiImpl(SchoolRepository schoolRepository,
                          SchoolRoleRepository roleRepository,
                          SchoolMembershipRepository membershipRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.schoolRepository = schoolRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public MembershipView createSchool(UUID ownerUserId, String name, String slug, String timezone) {
        String finalSlug = (slug == null || slug.isBlank()) ? slugify(name) : slugify(slug);
        if (finalSlug.isBlank()) {
            throw ApiException.badRequest("invalid_slug", "A school slug is required.");
        }
        if (schoolRepository.existsBySlugAndDeletedAtIsNull(finalSlug)) {
            throw ApiException.conflict("slug_taken", "That school slug is already in use.");
        }

        School school = new School();
        school.setName(name);
        school.setSlug(finalSlug);
        if (timezone != null && !timezone.isBlank()) {
            school.setTimezone(timezone);
        }
        school = schoolRepository.save(school);

        Map<String, SchoolRole> roles = new java.util.HashMap<>();
        for (String roleCode : PermissionCatalog.SYSTEM_ROLES.keySet()) {
            SchoolRole role = new SchoolRole();
            role.setSchoolId(school.getId());
            role.setCode(roleCode);
            role.setName(displayName(roleCode));
            role.setSystem(true);
            role.setPermissions(PermissionCatalog.defaultPermissionsFor(roleCode));
            roles.put(roleCode, roleRepository.save(role));
        }

        SchoolMembership owner = new SchoolMembership();
        owner.setSchoolId(school.getId());
        owner.setUserId(ownerUserId);
        owner.setTitle("Owner");
        owner.getRoles().add(roles.get(PermissionCatalog.ROLE_OWNER));
        owner = membershipRepository.save(owner);

        MembershipView view = toView(owner, school);
        eventPublisher.publishEvent(new SchoolCreatedEvent(school.getId(), ownerUserId, owner.getId(), school.getName()));
        return view;
    }

    @Override
    public Optional<MembershipView> resolveMembership(UUID userId, UUID schoolId) {
        return membershipRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId)
                .flatMap(membership -> schoolRepository.findById(schoolId)
                        .map(school -> toView(membership, school)));
    }

    @Override
    public List<MembershipSummary> listMemberships(UUID userId) {
        List<SchoolMembership> memberships = membershipRepository.findByUserIdAndDeletedAtIsNull(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<UUID, School> schools = new java.util.HashMap<>();
        schoolRepository.findByIdIn(memberships.stream().map(SchoolMembership::getSchoolId).toList())
                .forEach(s -> schools.put(s.getId(), s));
        List<MembershipSummary> result = new ArrayList<>();
        for (SchoolMembership membership : memberships) {
            School school = schools.get(membership.getSchoolId());
            if (school == null || school.getStatus().equals("closed")) {
                continue;
            }
            result.add(new MembershipSummary(
                    membership.getId(), membership.getUserId(), school.getId(), school.getName(), school.getSlug(),
                    membership.getStatus(), membership.getTitle(), roleCodes(membership)));
        }
        return result;
    }

    @Override
    public Optional<MembershipSummary> requireMembership(UUID membershipId) {
        return membershipRepository.findById(membershipId).flatMap(membership ->
                schoolRepository.findById(membership.getSchoolId()).map(school -> new MembershipSummary(
                        membership.getId(), membership.getUserId(), school.getId(), school.getName(),
                        school.getSlug(), membership.getStatus(), membership.getTitle(), roleCodes(membership))));
    }

    @Override
    public Map<String, Object> schoolSettings(UUID schoolId) {
        return schoolRepository.findById(schoolId).map(School::getSettings).orElseGet(Map::of);
    }

    @Override
    @Transactional
    public MembershipView ensureMembership(UUID schoolId, UUID userId, String roleCode) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> ApiException.notFound("school_not_found", "School not found."));

        SchoolRole role = roleRepository.findBySchoolIdAndCodeAndDeletedAtIsNull(schoolId, roleCode)
                .orElseGet(() -> {
                    SchoolRole newRole = new SchoolRole();
                    newRole.setSchoolId(schoolId);
                    newRole.setCode(roleCode);
                    newRole.setName(displayName(roleCode));
                    newRole.setSystem(true);
                    newRole.setPermissions(PermissionCatalog.defaultPermissionsFor(roleCode));
                    return roleRepository.save(newRole);
                });

        SchoolMembership membership = membershipRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId)
                .orElseGet(() -> {
                    SchoolMembership newM = new SchoolMembership();
                    newM.setSchoolId(schoolId);
                    newM.setUserId(userId);
                    newM.setStatus("active");
                    return newM;
                });

        membership.setStatus("active");
        if (membership.getRoles().stream().noneMatch(r -> r.getCode().equalsIgnoreCase(roleCode))) {
            membership.getRoles().add(role);
        }
        membership = membershipRepository.save(membership);
        return toView(membership, school);
    }

    private MembershipView toView(SchoolMembership membership, School school) {
        Set<String> permissions = new LinkedHashSet<>();
        for (SchoolRole role : membership.getRoles()) {
            permissions.addAll(role.getPermissions());
        }
        return new MembershipView(
                membership.getId(), school.getId(), school.getName(), membership.getUserId(),
                membership.getStatus(), membership.getTitle(), permissions, roleCodes(membership));
    }

    private List<String> roleCodes(SchoolMembership membership) {
        return membership.getRoles().stream().map(SchoolRole::getCode).toList();
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String displayName(String roleCode) {
        String[] parts = roleCode.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
