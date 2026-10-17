package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Student> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, List<UUID> ids);

    Optional<Student> findBySchoolIdAndUserIdAndDeletedAtIsNull(UUID schoolId, UUID userId);

    Optional<Student> findBySchoolIdAndAdmissionNumberAndDeletedAtIsNull(UUID schoolId, String admissionNumber);

    boolean existsBySchoolIdAndAdmissionNumberAndDeletedAtIsNull(UUID schoolId, String admissionNumber);

    long countBySchoolIdAndDeletedAtIsNull(UUID schoolId);

    @Query("""
            SELECT s FROM Student s
            WHERE s.schoolId = :schoolId AND s.deletedAt IS NULL
              AND (:q IS NULL OR lower(s.firstName) LIKE :q OR lower(s.lastName) LIKE :q
                   OR lower(s.admissionNumber) LIKE :q)
              AND (:classGroupId IS NULL OR EXISTS (SELECT e.id FROM Enrollment e
                   WHERE e.studentId = s.id AND e.classGroupId = :classGroupId AND e.deletedAt IS NULL))
              AND (:streamId IS NULL OR EXISTS (SELECT e.id FROM Enrollment e
                   WHERE e.studentId = s.id AND e.streamId = :streamId AND e.deletedAt IS NULL))
            """)
    Page<Student> search(@Param("schoolId") UUID schoolId, @Param("q") String q,
                         @Param("classGroupId") UUID classGroupId, @Param("streamId") UUID streamId,
                         Pageable pageable);
}
