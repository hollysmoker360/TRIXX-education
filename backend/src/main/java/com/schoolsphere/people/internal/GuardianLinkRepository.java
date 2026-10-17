package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, UUID> {

    List<GuardianLink> findBySchoolIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID studentId);

    List<GuardianLink> findBySchoolIdAndParentIdAndDeletedAtIsNull(UUID schoolId, UUID parentId);

    Optional<GuardianLink> findBySchoolIdAndParentIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID parentId,
                                                                                   UUID studentId);

    Optional<GuardianLink> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    boolean existsBySchoolIdAndParentIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID parentId, UUID studentId);
}
