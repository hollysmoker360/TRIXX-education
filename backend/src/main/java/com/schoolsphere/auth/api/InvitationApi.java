package com.schoolsphere.auth.api;

import java.time.Instant;
import java.util.UUID;

public interface InvitationApi {

    CreatedInvitation createInvitation(String email, String roleCode, UUID schoolId, UUID childStudentId, UUID invitedById);

    InvitationDetails getInvitationDetails(String token);

    CompletedInvitation completeInvitation(String token, String displayName, String password, String phone);

    record CreatedInvitation(
            UUID id,
            String token,
            String email,
            String roleCode,
            UUID schoolId,
            UUID childStudentId,
            String setupUrl) {
    }

    record InvitationDetails(
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

    record CompletedInvitation(
            boolean success,
            String message,
            String email,
            UUID userId) {
    }
}
