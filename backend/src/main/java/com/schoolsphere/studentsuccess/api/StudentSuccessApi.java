package com.schoolsphere.studentsuccess.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Public API of the Student Success module (spec section 2.13).
 * Advisory indicators only — staff review recommended, no automatic labeling.
 */
public interface StudentSuccessApi {

    record StudentProfileDTO(
            UUID id,
            UUID studentId,
            String studentName,
            String admissionNumber,
            String overallRiskLevel,
            Boolean attendanceConcern,
            Boolean academicDecline,
            Boolean incompleteWork,
            LocalDate lastReviewedAt) {
    }

    record RiskIndicatorDTO(
            UUID id,
            UUID studentId,
            String studentName,
            String indicatorType,
            String severity,
            String description,
            String sourceModule,
            UUID sourceEntityId,
            Boolean acknowledged,
            Boolean resolved) {
    }

    record RiskIndicatorInput(
            UUID studentId,
            String indicatorType,
            String severity,
            String description,
            String sourceModule,
            UUID sourceEntityId) {
    }

    record SupportReferralDTO(
            UUID id,
            UUID studentId,
            String studentName,
            UUID referredBy,
            String referredByName,
            UUID referredTo,
            String referredToName,
            String referralType,
            String priority,
            String reason,
            String status,
            String outcomeNotes) {
    }

    record SupportReferralInput(
            UUID studentId,
            String referralType,
            String priority,
            String reason,
            UUID referredTo) {
    }

    record InterventionPlanDTO(
            UUID id,
            UUID studentId,
            String studentName,
            UUID referralId,
            String title,
            String description,
            LocalDate startDate,
            LocalDate reviewDate,
            LocalDate endDate,
            String status,
            UUID coordinatorMembershipId,
            String coordinatorName,
            Boolean guardianNotified,
            Boolean guardianConsent) {
    }

    record InterventionPlanInput(
            UUID studentId,
            UUID referralId,
            String title,
            String description,
            LocalDate startDate,
            LocalDate reviewDate,
            LocalDate endDate,
            String status,
            UUID coordinatorMembershipId,
            Boolean guardianNotified,
            Boolean guardianConsent) {
    }

    record SupportGoalDTO(
            UUID id,
            UUID planId,
            String description,
            LocalDate targetDate,
            Boolean achieved,
            LocalDateTime achievedAt,
            String evidenceRef) {
    }

    record SupportGoalInput(
            UUID planId,
            String description,
            LocalDate targetDate,
            String evidenceRef) {
    }

    record ProgressNoteDTO(
            UUID id,
            UUID planId,
            UUID studentId,
            UUID authorMembershipId,
            String authorName,
            String noteType,
            String content,
            Boolean isConfidential) {
    }

    record ProgressNoteInput(
            UUID planId,
            UUID studentId,
            String noteType,
            String content,
            Boolean isConfidential) {
    }

    record TimelineEvent(
            String eventType,
            String title,
            String description,
            LocalDate occurredAt,
            UUID entityId) {
    }

    StudentProfileDTO getProfile(UUID studentId);

    List<RiskIndicatorDTO> listIndicators(UUID studentId, Boolean acknowledged, Boolean resolved);

    RiskIndicatorDTO createIndicator(RiskIndicatorInput input);

    RiskIndicatorDTO acknowledgeIndicator(UUID indicatorId);

    RiskIndicatorDTO resolveIndicator(UUID indicatorId);

    List<SupportReferralDTO> listReferrals(UUID studentId, String status);

    SupportReferralDTO createReferral(SupportReferralInput input);

    SupportReferralDTO updateReferralStatus(UUID referralId, String status, String outcomeNotes);

    List<InterventionPlanDTO> listInterventionPlans(UUID studentId);

    InterventionPlanDTO createInterventionPlan(InterventionPlanInput input);

    InterventionPlanDTO updateInterventionPlan(UUID planId, InterventionPlanInput input);

    List<SupportGoalDTO> listGoals(UUID planId);

    SupportGoalDTO createGoal(SupportGoalInput input);

    SupportGoalDTO markGoalAchieved(UUID goalId);

    List<ProgressNoteDTO> listProgressNotes(UUID planId, Boolean confidentialOnly);

    ProgressNoteDTO createProgressNote(ProgressNoteInput input);

    List<TimelineEvent> studentTimeline(UUID studentId);
}
