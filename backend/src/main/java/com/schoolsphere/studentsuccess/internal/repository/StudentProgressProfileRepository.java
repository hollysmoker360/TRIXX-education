package com.schoolsphere.studentsuccess.internal.repository;

import com.schoolsphere.studentsuccess.internal.entity.StudentProgressProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProgressProfileRepository extends JpaRepository<StudentProgressProfile, UUID> {

    Optional<StudentProgressProfile> findBySchoolIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID studentId);
}
