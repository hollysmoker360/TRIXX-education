package com.schoolsphere.elearning.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    List<CourseEnrollment> findBySchoolIdAndCourseIdAndDeletedAtIsNull(UUID schoolId, UUID courseId);

    List<CourseEnrollment> findBySchoolIdAndCourseIdInAndDeletedAtIsNull(UUID schoolId, Collection<UUID> courseIds);

    List<CourseEnrollment> findBySchoolIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID studentId);

    Optional<CourseEnrollment> findBySchoolIdAndCourseIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID courseId,
                                                                                       UUID studentId);
}
