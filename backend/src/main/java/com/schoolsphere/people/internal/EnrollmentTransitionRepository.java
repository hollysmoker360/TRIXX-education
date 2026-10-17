package com.schoolsphere.people.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentTransitionRepository extends JpaRepository<EnrollmentTransition, UUID> {

    List<EnrollmentTransition> findBySchoolIdAndEnrollmentIdOrderByCreatedAtAsc(UUID schoolId, UUID enrollmentId);
}
