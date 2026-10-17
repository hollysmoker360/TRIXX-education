package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    List<Enrollment> findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId);

    List<Enrollment> findBySchoolIdAndAcademicYearIdAndDeletedAtIsNull(UUID schoolId, UUID academicYearId);

    List<Enrollment> findBySchoolIdAndClassGroupIdAndDeletedAtIsNull(UUID schoolId, UUID classGroupId);

    List<Enrollment> findBySchoolIdAndAcademicYearIdAndClassGroupIdAndDeletedAtIsNull(UUID schoolId, UUID academicYearId,
                                                                                      UUID classGroupId);

    List<Enrollment> findBySchoolIdAndClassGroupIdAndStatusInAndDeletedAtIsNull(UUID schoolId, UUID classGroupId,
                                                                                List<String> statuses);

    Optional<Enrollment> findBySchoolIdAndStudentIdAndAcademicYearIdAndDeletedAtIsNull(UUID schoolId, UUID studentId,
                                                                                       UUID academicYearId);

    Optional<Enrollment> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Enrollment> findBySchoolIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID studentId);

    Optional<Enrollment> findFirstBySchoolIdAndStudentIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID schoolId, UUID studentId, List<String> statuses);
}
