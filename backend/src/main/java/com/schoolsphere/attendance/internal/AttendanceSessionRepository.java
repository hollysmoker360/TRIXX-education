package com.schoolsphere.attendance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    Optional<AttendanceSession> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<AttendanceSession> findBySchoolIdAndSessionDateAndDeletedAtIsNull(UUID schoolId, LocalDate sessionDate);

    List<AttendanceSession> findBySchoolIdAndClassGroupIdAndDeletedAtIsNullOrderBySessionDateDesc(
            UUID schoolId, UUID classGroupId);

    List<AttendanceSession> findBySchoolIdAndClassGroupIdAndSessionDateAndDeletedAtIsNull(
            UUID schoolId, UUID classGroupId, LocalDate sessionDate);

    List<AttendanceSession> findBySchoolIdAndClassGroupIdAndSessionDateAndSessionTypeAndStatusAndDeletedAtIsNull(
            UUID schoolId, UUID classGroupId, LocalDate sessionDate, String sessionType, String status);
}
