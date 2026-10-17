package com.schoolsphere.academics.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the academics module (spec 4.4). */
public interface AcademicsApi {

    Optional<ClassInfo> findClass(UUID schoolId, UUID classGroupId);

    List<ClassInfo> findClasses(UUID schoolId, List<UUID> classGroupIds);

    Optional<SubjectInfo> findSubject(UUID schoolId, UUID subjectId);

    List<SubjectInfo> findSubjects(UUID schoolId, List<UUID> subjectIds);

    record ClassInfo(UUID id, String name) {
    }

    record SubjectInfo(UUID id, String name, String code) {
    }
}
