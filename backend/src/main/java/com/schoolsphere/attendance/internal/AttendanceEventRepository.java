package com.schoolsphere.attendance.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {

    Optional<AttendanceEvent> findBySchoolIdAndId(UUID schoolId, UUID id);

    /** Latest effective event per student (event-time corrections supersede earlier rows). */
    @Query(value = """
            SELECT DISTINCT ON (student_id) * FROM attendance_event
            WHERE session_id = :sessionId AND school_id = :schoolId
            ORDER BY student_id, received_at DESC, created_at DESC
            """, nativeQuery = true)
    List<AttendanceEvent> findLatestPerStudent(@Param("sessionId") UUID sessionId,
                                               @Param("schoolId") UUID schoolId);

    List<AttendanceEvent> findBySchoolIdAndStudentIdAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            UUID schoolId, UUID studentId, Instant from, Instant to);

    List<AttendanceEvent> findBySchoolIdAndStudentIdInAndEventTimeGreaterThanEqualAndEventTimeLessThan(
            UUID schoolId, List<UUID> studentIds, Instant from, Instant to);
}
