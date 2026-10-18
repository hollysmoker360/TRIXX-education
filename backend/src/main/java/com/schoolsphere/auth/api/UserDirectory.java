package com.schoolsphere.auth.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the auth module: user records for other modules (spec 4.4). */
public interface UserDirectory {

    Optional<UserRef> findById(UUID id);

    Optional<UserRef> findByEmail(String email);

    List<UserRef> findAllByIdIn(List<UUID> ids);

    boolean isPlatformAdmin(UUID userId);

    /** Creates a user with a generated temporary password; the caller surfaces it once. */
    CreatedUser createUserWithTemporaryPassword(String email, String displayName, String phone);

    record UserRef(UUID id, String email, String displayName, String phone, String status, boolean isPlatformAdmin) {
    }

    record CreatedUser(UserRef user, String temporaryPassword) {
    }
}
