package com.schoolsphere.studentsuccess.web;

import com.schoolsphere.studentsuccess.api.StudentSuccessApi;
import com.schoolsphere.studentsuccess.internal.StudentSuccessService;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/student-success")
public class StudentSuccessController {

    private final StudentSuccessService service;

    public StudentSuccessController(StudentSuccessService service) {
        this.service = service;
    }

    // ----- profile -----

    @GetMapping("/profiles/{studentId}")
    @PreAuthorize("@policy.can('student_success.view')")
    public StudentSuccessApi.StudentProfileDTO getProfile(@PathVariable UUID studentId) {
        return service.getProfile(studentId);
    }

    // ----- indicators -----

    @GetMapping("/indicators")
    @PreAuthorize("@policy.can('student_success.view')")
    public List<StudentSuccessApi.RiskIndicatorDTO> listIndicators(
            @RequestParam UUID studentId,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false) Boolean resolved) {
        return service.listIndicators(studentId, acknowledged, resolved);
    }

    @PostMapping("/indicators")
    @PreAuthorize("@policy.can('student_success.manage')")
    public StudentSuccessApi.RiskIndicatorDTO createIndicator(
            @RequestBody StudentSuccessApi.RiskIndicatorInput input) {
        return service.createIndicator(input);
    }

    @PostMapping("/indicators/{id}/acknowledge")
    @PreAuthorize("@policy.can('student_success.manage')")
    public StudentSuccessApi.RiskIndicatorDTO acknowledgeIndicator(@PathVariable UUID id) {
        return service.acknowledgeIndicator(id);
    }

    @PostMapping("/indicators/{id}/resolve")
    @PreAuthorize("@policy.can('student_success.manage')")
    public StudentSuccessApi.RiskIndicatorDTO resolveIndicator(@PathVariable UUID id) {
        return service.resolveIndicator(id);
    }

    // ----- referrals -----

    @GetMapping("/referrals")
    @PreAuthorize("@policy.can('student_success.view')")
    public List<StudentSuccessApi.SupportReferralDTO> listReferrals(
            @RequestParam UUID studentId,
            @RequestParam(required = false) String status) {
        return service.listReferrals(studentId, status);
    }

    @PostMapping("/referrals")
    @PreAuthorize("@policy.can('student_success.refer')")
    public StudentSuccessApi.SupportReferralDTO createReferral(
            @RequestBody StudentSuccessApi.SupportReferralInput input) {
        return service.createReferral(input);
    }

    @PutMapping("/referrals/{id}/status")
    @PreAuthorize("@policy.can('student_success.manage')")
    public StudentSuccessApi.SupportReferralDTO updateReferralStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false) String outcomeNotes) {
        return service.updateReferralStatus(id, status, outcomeNotes);
    }

    // ----- intervention plans -----

    @GetMapping("/intervention-plans")
    @PreAuthorize("@policy.can('student_success.view')")
    public List<StudentSuccessApi.InterventionPlanDTO> listInterventionPlans(
            @RequestParam UUID studentId) {
        return service.listInterventionPlans(studentId);
    }

    @PostMapping("/intervention-plans")
    @PreAuthorize("@policy.can('student_success.intervene')")
    public StudentSuccessApi.InterventionPlanDTO createInterventionPlan(
            @RequestBody StudentSuccessApi.InterventionPlanInput input) {
        return service.createInterventionPlan(input);
    }

    @PutMapping("/intervention-plans/{id}")
    @PreAuthorize("@policy.can('student_success.intervene')")
    public StudentSuccessApi.InterventionPlanDTO updateInterventionPlan(
            @PathVariable UUID id,
            @RequestBody StudentSuccessApi.InterventionPlanInput input) {
        return service.updateInterventionPlan(id, input);
    }

    // ----- goals -----

    @GetMapping("/intervention-plans/{planId}/goals")
    @PreAuthorize("@policy.can('student_success.view')")
    public List<StudentSuccessApi.SupportGoalDTO> listGoals(@PathVariable UUID planId) {
        return service.listGoals(planId);
    }

    @PostMapping("/intervention-plans/{planId}/goals")
    @PreAuthorize("@policy.can('student_success.intervene')")
    public StudentSuccessApi.SupportGoalDTO createGoal(
            @PathVariable UUID planId,
            @RequestBody StudentSuccessApi.SupportGoalInput input) {
        return service.createGoal(input);
    }

    @PostMapping("/goals/{id}/achieve")
    @PreAuthorize("@policy.can('student_success.intervene')")
    public StudentSuccessApi.SupportGoalDTO markGoalAchieved(@PathVariable UUID id) {
        return service.markGoalAchieved(id);
    }

    // ----- progress notes -----

    @GetMapping("/intervention-plans/{planId}/notes")
    @PreAuthorize("@policy.can('student_success.view') or @policy.can('student_success.view.confidential')")
    public List<StudentSuccessApi.ProgressNoteDTO> listProgressNotes(
            @PathVariable UUID planId,
            @RequestParam(required = false) Boolean confidentialOnly) {
        return service.listProgressNotes(planId, confidentialOnly);
    }

    @PostMapping("/intervention-plans/{planId}/notes")
    @PreAuthorize("@policy.can('student_success.manage')")
    public StudentSuccessApi.ProgressNoteDTO createProgressNote(
            @PathVariable UUID planId,
            @RequestBody StudentSuccessApi.ProgressNoteInput input) {
        return service.createProgressNote(input);
    }

    // ----- timeline -----

    @GetMapping("/students/{studentId}/timeline")
    @PreAuthorize("@policy.can('student_success.view')")
    public List<StudentSuccessApi.TimelineEvent> studentTimeline(@PathVariable UUID studentId) {
        return service.studentTimeline(studentId);
    }
}
