package com.schoolsphere.tenants.web;

import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.tenants.api.TenantsApi;
import com.schoolsphere.tenants.internal.SchoolService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {

    private final SchoolService schoolService;
    private final TenantsApi tenantsApi;

    public SchoolController(SchoolService schoolService, TenantsApi tenantsApi) {
        this.schoolService = schoolService;
        this.tenantsApi = tenantsApi;
    }

    @PostMapping
    public TenantsApi.MembershipView createSchool(@Valid @RequestBody CreateSchoolRequest request) {
        return tenantsApi.createSchool(SecurityUtils.requireUserId(), request.name(), request.slug(),
                request.timezone());
    }

    @GetMapping("/current")
    @PreAuthorize("@policy.can('school.view')")
    public SchoolService.SchoolView currentSchool() {
        return schoolService.currentSchool();
    }

    @PutMapping("/current")
    @PreAuthorize("@policy.can('school.settings.manage')")
    public SchoolService.SchoolView updateSchool(@RequestBody Map<String, Object> patch) {
        return schoolService.updateSchool(patch);
    }

    @GetMapping("/current/members")
    @PreAuthorize("@policy.can('people.view')")
    public List<SchoolService.MemberView> members() {
        return schoolService.listMemberships();
    }

    @PostMapping("/current/members")
    @PreAuthorize("@policy.can('people.manage')")
    public SchoolService.InviteResult invite(@Valid @RequestBody InviteRequest request) {
        return schoolService.inviteMember(request.email(), request.displayName(), request.roles(), request.title());
    }

    @PatchMapping("/current/members/{membershipId}/status")
    @PreAuthorize("@policy.can('people.manage')")
    public SchoolService.MemberView setStatus(@PathVariable UUID membershipId,
                                              @Valid @RequestBody StatusRequest request) {
        return schoolService.setMembershipStatus(membershipId, request.status());
    }

    @PutMapping("/current/members/{membershipId}/roles")
    @PreAuthorize("@policy.can('people.manage')")
    public SchoolService.MemberView setRoles(@PathVariable UUID membershipId,
                                             @Valid @RequestBody RolesRequest request) {
        return schoolService.setMembershipRoles(membershipId, request.roles());
    }

    @GetMapping("/current/roles")
    @PreAuthorize("@policy.can('people.view')")
    public List<SchoolService.RoleView> roles() {
        return schoolService.listRoles();
    }

    @PostMapping("/current/roles")
    @PreAuthorize("@policy.can('school.settings.manage')")
    public SchoolService.RoleView createRole(@Valid @RequestBody CreateRoleRequest request) {
        return schoolService.createRole(request.code(), request.name(), request.permissions());
    }

    @PatchMapping("/current/roles/{roleId}")
    @PreAuthorize("@policy.can('school.settings.manage')")
    public SchoolService.RoleView updateRole(@PathVariable UUID roleId,
                                             @Valid @RequestBody UpdateRoleRequest request) {
        return schoolService.updateRole(roleId, request.name(), request.permissions());
    }

    public record CreateSchoolRequest(@NotBlank String name, String slug, String timezone) {
    }

    public record InviteRequest(@NotBlank String email, String displayName,
                                @NotEmpty List<String> roles, String title) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record RolesRequest(@NotEmpty List<String> roles) {
    }

    public record CreateRoleRequest(@NotBlank String code, @NotBlank String name, Set<String> permissions) {
    }

    public record UpdateRoleRequest(String name, Set<String> permissions) {
    }
}
