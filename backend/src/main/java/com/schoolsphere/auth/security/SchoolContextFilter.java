package com.schoolsphere.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.common.security.CurrentPrincipal;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.tenants.api.TenantsApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the client-supplied school selection (X-School-Id) into a validated membership and sets
 * the tenant context (spec 7.1). The tenant is always derived from the authenticated principal's
 * memberships; a school id the user does not belong to is rejected. Without the header the request
 * proceeds without tenant context (global endpoints only).
 */
public class SchoolContextFilter extends OncePerRequestFilter {

    public static final String SCHOOL_HEADER = "X-School-Id";

    private final TenantsApi tenantsApi;
    private final ObjectMapper objectMapper;

    public SchoolContextFilter(TenantsApi tenantsApi, ObjectMapper objectMapper) {
        this.tenantsApi = tenantsApi;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CurrentPrincipal principal = SecurityUtils.principalOrNull();
        String schoolHeader = request.getHeader(SCHOOL_HEADER);

        if (principal != null && schoolHeader != null && !schoolHeader.isBlank()) {
            UUID schoolId;
            try {
                schoolId = UUID.fromString(schoolHeader.trim());
            } catch (IllegalArgumentException e) {
                writeProblem(response, HttpStatus.BAD_REQUEST, "invalid_school_id", "The X-School-Id header is not a valid id.");
                return;
            }
            var membership = tenantsApi.resolveMembership(principal.userId(), schoolId).orElse(null);
            if (membership == null) {
                writeProblem(response, HttpStatus.FORBIDDEN, "membership_not_found",
                        "You are not a member of this school.");
                return;
            }
            if (!"active".equals(membership.status())) {
                writeProblem(response, HttpStatus.FORBIDDEN, "membership_" + membership.status(),
                        "Your membership in this school is " + membership.status() + ".");
                return;
            }

            CurrentPrincipal scoped = new CurrentPrincipal(
                    principal.userId(), principal.displayName(), principal.email(),
                    schoolId, membership.membershipId(), membership.permissions(), principal.deviceId());
            var authorities = membership.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(scoped, null, authorities));
            TenantContext.set(schoolId, principal.userId(), membership.membershipId(), principal.deviceId());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create("https://schoolsphere.dev/errors/" + code));
        pd.setTitle(status.getReasonPhrase());
        pd.setDetail(detail);
        pd.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
