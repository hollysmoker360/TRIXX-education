package com.schoolsphere.timetable.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {

    Optional<TimetableEntry> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<TimetableEntry> findBySchoolIdAndVersionIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
            UUID schoolId, UUID versionId);

    List<TimetableEntry> findBySchoolIdAndVersionIdAndClassGroupIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
            UUID schoolId, UUID versionId, UUID classGroupId);

    List<TimetableEntry> findBySchoolIdAndVersionIdAndTeacherMembershipIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
            UUID schoolId, UUID versionId, UUID teacherMembershipId);
}
