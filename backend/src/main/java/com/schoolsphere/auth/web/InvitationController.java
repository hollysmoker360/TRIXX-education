package com.schoolsphere.auth.web;

import com.schoolsphere.auth.api.InvitationApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationApi invitationApi;

    public InvitationController(InvitationApi invitationApi) {
        this.invitationApi = invitationApi;
    }

    @GetMapping("/{token}")
    public ResponseEntity<InvitationDetailsResponse> getInvitationDetails(@PathVariable String token) {
        InvitationApi.InvitationDetails details = invitationApi.getInvitationDetails(token);

        return ResponseEntity.ok(new InvitationDetailsResponse(
                details.token(),
                details.email(),
                details.roleCode(),
                details.schoolId(),
                details.schoolName(),
                details.childStudentId(),
                details.studentName(),
                details.status(),
                details.expiresAt()
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteInvitationResponse> completeInvitation(@Valid @RequestBody CompleteInvitationRequest request) {
        InvitationApi.CompletedInvitation result = invitationApi.completeInvitation(
                request.token(),
                request.displayName(),
                request.password(),
                request.phone()
        );

        return ResponseEntity.ok(new CompleteInvitationResponse(
                result.success(),
                result.message(),
                result.email(),
                result.userId()
        ));
    }

    public record InvitationDetailsResponse(
            String token,
            String email,
            String roleCode,
            UUID schoolId,
            String schoolName,
            UUID childStudentId,
            String studentName,
            String status,
            Instant expiresAt) {
    }

    public record CompleteInvitationRequest(
            @NotBlank String token,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 100) String password,
            String phone) {
    }

    public record CompleteInvitationResponse(
            boolean success,
            String message,
            String email,
            UUID userId) {
    }
}
