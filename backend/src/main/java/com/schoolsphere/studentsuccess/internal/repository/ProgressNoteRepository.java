package com.schoolsphere.studentsuccess.internal.repository;

import com.schoolsphere.studentsuccess.internal.entity.ProgressNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressNoteRepository extends JpaRepository<ProgressNote, UUID> {

    List<ProgressNote> findBySchoolIdAndPlanIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID schoolId, UUID planId);
}
