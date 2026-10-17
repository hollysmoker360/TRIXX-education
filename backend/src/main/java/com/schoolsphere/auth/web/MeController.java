package com.schoolsphere.auth.web;

import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.security.CurrentPrincipal;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.tenants.api.TenantsApi;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserDirectory userDirectory;
    private final TenantsApi tenantsApi;

    public MeController(UserDirectory userDirectory, TenantsApi tenantsApi) {
        this.userDirectory = userDirectory;
        this.tenantsApi = tenantsApi;
    }

    @GetMapping
    public MeResponse me() {
        CurrentPrincipal principal = SecurityUtils.requirePrincipal();
        UserDirectory.UserRef user = userDirectory.findById(principal.userId())
                .orElseThrow(() -> com.schoolsphere.common.error.ApiException.notFound("user_not_found", "User not found."));
        List<TenantsApi.MembershipSummary> memberships = tenantsApi.listMemberships(principal.userId());
        return new MeResponse(user, memberships,
                principal.schoolId(), principal.membershipId(), principal.permissions().stream().sorted().toList());
    }

    public record MeResponse(UserDirectory.UserRef user,
                             List<TenantsApi.MembershipSummary> memberships,
                             java.util.UUID activeSchoolId,
                             java.util.UUID activeMembershipId,
                             List<String> permissions) {
    }
}
