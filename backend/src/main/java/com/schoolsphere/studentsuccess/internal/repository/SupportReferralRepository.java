package com.schoolsphere.studentsuccess.internal.repository;

import com.schoolsphere.studentsuccess.internal.entity.SupportReferral;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportReferralRepository extends JpaRepository<SupportReferral, UUID> {

    Optional<SupportReferral> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<SupportReferral> findBySchoolIdAndStudentIdAndDeletedAtIsNull(
            UUID schoolId, UUID studentId);

    List<SupportReferral> findBySchoolIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID schoolId, String status);
}
