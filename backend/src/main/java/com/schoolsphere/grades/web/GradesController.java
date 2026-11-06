package com.schoolsphere.grades.web;

import com.schoolsphere.grades.api.GradesApi;
import com.schoolsphere.grades.internal.GradesService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grades")
public class GradesController {

    private final GradesService gradesService;

    public GradesController(GradesService gradesService) {
        this.gradesService = gradesService;
    }

    // ----- assessments -----

    @PostMapping("/assessments")
    @PreAuthorize("@policy.can('grade.enter')")
    public GradesApi.AssessmentView createAssessment(@RequestBody GradesApi.AssessmentDTO dto) {
        return gradesService.createAssessment(dto);
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize("@policy.can('grade.view')")
    public GradesApi.AssessmentView getAssessment(@PathVariable UUID id) {
        return gradesService.getAssessment(id);
    }

    @GetMapping("/assessments")
    @PreAuthorize("@policy.can('grade.view')")
    public List<GradesApi.AssessmentView> listAssessments(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID termId,
            @RequestParam(required = false) Boolean published) {
        return gradesService.listAssessments(classGroupId, subjectId, termId, published);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("@policy.can('grade.enter')")
    public GradesApi.AssessmentView updateAssessment(@PathVariable UUID id,
                                                     @RequestBody GradesApi.AssessmentDTO dto) {
        return gradesService.updateAssessment(id, dto);
    }

    @DeleteMapping("/assessments/{id}")
    @PreAuthorize("@policy.can('grade.enter')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable UUID id) {
        gradesService.deleteAssessment(id);
        return ResponseEntity.noContent().build();
    }

    // ----- grade entries -----

    @PostMapping("/assessments/{id}/entries")
    @PreAuthorize("@policy.can('grade.enter')")
    public List<GradesApi.GradeEntryView> enterGrades(@PathVariable UUID id,
                                                      @RequestBody List<GradesApi.GradeEntryInput> inputs) {
        return gradesService.enterGrades(id, inputs);
    }

    @PutMapping("/entries/{entryId}")
    @PreAuthorize("@policy.can('grade.enter')")
    public GradesApi.GradeEntryView updateGradeEntry(@PathVariable UUID entryId,
                                                     @RequestBody GradesApi.GradeEntryInput input) {
        return gradesService.updateGradeEntry(entryId, input);
    }

    @PostMapping("/assessments/{id}/publish")
    @PreAuthorize("@policy.can('grade.publish')")
    public GradesApi.AssessmentView publishAssessment(@PathVariable UUID id) {
        return gradesService.publishAssessment(id);
    }

    @PostMapping("/entries/{entryId}/correct")
    @PreAuthorize("@policy.can('grade.moderate')")
    public GradesApi.GradeEntryView requestCorrection(@PathVariable UUID entryId,
                                                      @RequestBody GradesApi.CorrectionRequest request) {
        return gradesService.requestCorrection(entryId, request);
    }

    @PostMapping("/corrections/{correctionId}/approve")
    @PreAuthorize("@policy.can('grade.moderate')")
    public GradesApi.GradeEntryView approveCorrection(@PathVariable UUID correctionId) {
        return gradesService.approveCorrection(correctionId);
    }

    // ----- student / class views -----

    @GetMapping("/students/{studentId}/grades")
    @PreAuthorize("@policy.can('grade.view.own') or @policy.can('grade.view')")
    public List<GradesApi.GradeEntryView> studentGrades(@PathVariable UUID studentId) {
        return gradesService.studentGrades(studentId);
    }

    @GetMapping("/classes/{classId}/gradebook")
    @PreAuthorize("@policy.can('grade.view')")
    public List<GradesApi.ClassGradebookRow> classGradebook(@PathVariable UUID classId,
                                                            @RequestParam(required = false) UUID termId) {
        return gradesService.classGradebook(classId, termId);
    }

    // ----- grade bands -----

    @GetMapping("/bands")
    @PreAuthorize("@policy.can('grade.view')")
    public List<GradesApi.GradebandDTO> listGradeBands() {
        return gradesService.listGradeBands();
    }

    @PostMapping("/bands")
    @PreAuthorize("@policy.can('grade.enter')")
    public GradesApi.GradebandDTO createGradeBand(@RequestBody GradesApi.GradebandDTO dto) {
        return gradesService.createGradeBand(dto);
    }
}
