package com.schoolsphere.timetable.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableVersionRepository extends JpaRepository<TimetableVersion, UUID> {

    Optional<TimetableVersion> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<TimetableVersion> findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId);

    List<TimetableVersion> findBySchoolIdAndTermIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId, UUID termId);

    Optional<TimetableVersion> findFirstBySchoolIdAndTermIdAndStateAndDeletedAtIsNull(UUID schoolId, UUID termId,
                                                                                      String state);
}
