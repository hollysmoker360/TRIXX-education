package com.schoolsphere.grades.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public API of the Marks Gradebook module (spec section 1). Assessments carry components;
 * grade entries support draft -> submitted -> moderated -> published lifecycle with auditable
 * corrections on published marks.
 */
public interface GradesApi {

    record AssessmentDTO(
            UUID id,
            UUID classGroupId,
            UUID subjectId,
            UUID teacherMembershipId,
            UUID termId,
            String title,
            String type,
            Double maxMarks,
            Double weight,
            LocalDate dateAssessed,
            LocalDate dueDate,
            UUID gradingScaleId,
            Boolean published,
            List<AssessmentComponentDTO> components) {
    }

    record AssessmentComponentDTO(
            UUID id,
            String name,
            Double maxMarks,
            Double weight,
            Integer position) {
    }

    record GradeEntryDTO(
            UUID id,
            UUID studentId,
            Double marksObtained,
            String grade,
            String feedback,
            String status) {
    }

    record GradeEntryInput(
            UUID studentId,
            Double marksObtained,
            String feedback) {
    }

    record GradeEntryView(
            UUID id,
            UUID studentId,
            String studentName,
            String admissionNumber,
            Double marksObtained,
            Double maxMarks,
            String grade,
            String feedback,
            String status,
            Boolean published) {
    }

    record AssessmentView(
            UUID id,
            UUID classGroupId,
            UUID subjectId,
            UUID teacherMembershipId,
            UUID termId,
            String title,
            String type,
            Double maxMarks,
            Double weight,
            LocalDate dateAssessed,
            LocalDate dueDate,
            UUID gradingScaleId,
            Boolean published,
            List<AssessmentComponentDTO> components,
            List<GradeEntryView> entries) {
    }

    record GradebandDTO(
            UUID id,
            String name,
            Double minScore,
            Double maxScore,
            Double gradePoint,
            String colorHex) {
    }

    record CorrectionRequest(
            Double previousMarks,
            Double newMarks,
            String reason) {
    }

    record ClassGradebookRow(
            UUID studentId,
            String studentName,
            String admissionNumber,
            List<GradeEntryView> entries,
            Double average) {
    }

    AssessmentView createAssessment(AssessmentDTO dto);

    AssessmentView getAssessment(UUID assessmentId);

    List<AssessmentView> listAssessments(UUID classGroupId, UUID subjectId, UUID termId, Boolean published);

    AssessmentView updateAssessment(UUID assessmentId, AssessmentDTO dto);

    void deleteAssessment(UUID assessmentId);

    List<GradeEntryView> enterGrades(UUID assessmentId, List<GradeEntryInput> inputs);

    GradeEntryView updateGradeEntry(UUID entryId, GradeEntryInput input);

    AssessmentView publishAssessment(UUID assessmentId);

    GradeEntryView requestCorrection(UUID entryId, CorrectionRequest request);

    GradeEntryView approveCorrection(UUID correctionId);

    List<GradebandDTO> listGradeBands();

    GradebandDTO createGradeBand(GradebandDTO dto);

    List<ClassGradebookRow> classGradebook(UUID classGroupId, UUID termId);

    List<GradeEntryView> studentGrades(UUID studentId);
}
