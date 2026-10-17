package com.schoolsphere.learning.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an assignment is published so other modules can react (e.g. student tasks).
 * Part of the learning module's public API (spec 4.4): other modules must never reach into
 * {@code learning.internal}.
 */
public record AssignmentPublishedEvent(UUID schoolId, UUID assignmentId, UUID classGroupId, UUID streamId,
                                       UUID subjectId, String title, Instant dueAt) {
}
