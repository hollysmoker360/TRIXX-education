package com.schoolsphere.learning.internal;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.persistence.TenantEntity;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.learning.api.AssignmentPublishedEvent;
import com.schoolsphere.people.api.PeopleApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Learning hub resources, bookmarks, completions, assignments, submissions, and grading (spec 11.2, 11.3). */
@Service
@TenantScoped
public class LearningService {

    private static final List<String> RESOURCE_TYPES = List.of("document", "presentation", "image", "video", "audio",
            "link", "text_lesson", "worksheet", "revision_pack");
    private static final List<String> LATE_POLICIES = List.of("accepted", "penalized", "rejected");

    private final LearningResourceRepository resourceRepository;
    private final ResourceVersionRepository versionRepository;
    private final ResourceBookmarkRepository bookmarkRepository;
    private final ResourceCompletionRepository completionRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final PeopleApi peopleApi;
    private final AcademicsApi academicsApi;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public LearningService(LearningResourceRepository resourceRepository,
                           ResourceVersionRepository versionRepository,
                           ResourceBookmarkRepository bookmarkRepository,
                           ResourceCompletionRepository completionRepository,
                           AssignmentRepository assignmentRepository,
                           SubmissionRepository submissionRepository,
                           PeopleApi peopleApi,
                           AcademicsApi academicsApi,
                           ApplicationEventPublisher eventPublisher,
                           AuditLogService auditLogService) {
        this.resourceRepository = resourceRepository;
        this.versionRepository = versionRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.completionRepository = completionRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.peopleApi = peopleApi;
        this.academicsApi = academicsApi;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    // ----- resources (management) -----

    public PageResponse<ResourceView> listResources(UUID classGroupId, UUID subjectId, String state, int page,
                                                    int size) {
        Page<LearningResource> result = resourceRepository.search(schoolId(), classGroupId, subjectId, state,
                pageRequest(page, size));
        return PageResponse.of(result.map(this::toResourceView));
    }

    public ResourceView getResource(UUID id) {
        return toResourceView(requireResource(id));
    }

    public ResourceView createResource(String title, String description, String resourceType, String topic,
                                       UUID subjectId, UUID classGroupId, UUID streamId, UUID academicYearId,
                                       UUID termId, Map<String, Object> audience) {
        validateResourceType(resourceType);
        validateScope(subjectId, classGroupId);
        LearningResource resource = new LearningResource();
        resource.setSchoolId(schoolId());
        resource.setTitle(title);
        resource.setDescription(description);
        resource.setResourceType(resourceType);
        resource.setTopic(topic);
        resource.setSubjectId(subjectId);
        resource.setClassGroupId(classGroupId);
        resource.setStreamId(streamId);
        resource.setAcademicYearId(academicYearId);
        resource.setTermId(termId);
        resource.setAudience(audience == null ? new LinkedHashMap<>() : new LinkedHashMap<>(audience));
        resource.setState("draft");
        resource.setLatestVersion(1);
        resource.setUploadedByMembershipId(TenantContext.actorMembershipId());
        resource.setCreatedBy(TenantContext.actorUserId());
        resource.setUpdatedBy(TenantContext.actorUserId());
        return toResourceView(resourceRepository.save(resource));
    }

    public ResourceView updateResource(UUID id, String title, String description, String resourceType, String topic,
                                       UUID subjectId, UUID classGroupId, UUID streamId, UUID academicYearId,
                                       UUID termId, Map<String, Object> audience) {
        LearningResource resource = requireResource(id);
        if (!"draft".equals(resource.getState())) {
            throw ApiException.conflict("resource_not_draft", "Only draft resources can be edited.");
        }
        if (title != null) {
            resource.setTitle(title);
        }
        if (description != null) {
            resource.setDescription(description);
        }
        if (resourceType != null) {
            validateResourceType(resourceType);
            resource.setResourceType(resourceType);
        }
        if (topic != null) {
            resource.setTopic(topic);
        }
        if (subjectId != null && classGroupId != null) {
            validateScope(subjectId, classGroupId);
            resource.setSubjectId(subjectId);
            resource.setClassGroupId(classGroupId);
        } else if (subjectId != null) {
            validateScope(subjectId, null);
            resource.setSubjectId(subjectId);
        } else if (classGroupId != null) {
            validateScope(null, classGroupId);
            resource.setClassGroupId(classGroupId);
        }
        if (streamId != null) {
            resource.setStreamId(streamId);
        }
        if (academicYearId != null) {
            resource.setAcademicYearId(academicYearId);
        }
        if (termId != null) {
            resource.setTermId(termId);
        }
        if (audience != null) {
            resource.setAudience(new LinkedHashMap<>(audience));
        }
        resource.setUpdatedBy(TenantContext.actorUserId());
        return toResourceView(resourceRepository.save(resource));
    }

    public ResourceView publishResource(UUID id, Instant publishAt) {
        LearningResource resource = requireResource(id);
        if ("archived".equals(resource.getState())) {
            throw ApiException.conflict("resource_archived", "An archived resource cannot be published.");
        }
        if (publishAt != null && publishAt.isAfter(Instant.now())) {
            resource.setState("scheduled");
            resource.setPublishAt(publishAt);
        } else {
            resource.setState("published");
            resource.setPublishAt(publishAt == null ? Instant.now() : publishAt);
        }
        resource.setUpdatedBy(TenantContext.actorUserId());
        return toResourceView(resourceRepository.save(resource));
    }

    public ResourceView archiveResource(UUID id) {
        LearningResource resource = requireResource(id);
        resource.setState("archived");
        resource.setUpdatedBy(TenantContext.actorUserId());
        return toResourceView(resourceRepository.save(resource));
    }

    public void deleteResource(UUID id) {
        LearningResource resource = requireResource(id);
        softDelete(resource);
        resourceRepository.save(resource);
    }

    /** Flips scheduled resources whose publish time has passed; called by {@link LearningScheduler}. */
    public int publishDueResources() {
        List<LearningResource> due = resourceRepository
                .findBySchoolIdAndStateAndPublishAtLessThanEqualAndDeletedAtIsNull(schoolId(), "scheduled",
                        Instant.now());
        for (LearningResource resource : due) {
            resource.setState("published");
            resourceRepository.save(resource);
        }
        return due.size();
    }

    // ----- versions -----

    public VersionView addResourceVersion(UUID resourceId, String storageKey, String mimeType, Long sizeBytes,
                                          String checksum, String externalUrl, String textBody, String note) {
        LearningResource resource = requireResource(resourceId);
        if ("link".equals(resource.getResourceType()) && (externalUrl == null || externalUrl.isBlank())) {
            throw ApiException.badRequest("external_url_required", "Link resources require an external URL.");
        }
        if ("text_lesson".equals(resource.getResourceType()) && (textBody == null || textBody.isBlank())) {
            throw ApiException.badRequest("text_body_required", "Text lessons require a text body.");
        }
        ResourceVersion version = new ResourceVersion();
        version.setSchoolId(schoolId());
        version.setResourceId(resourceId);
        version.setVersionNo(nextVersionNo(resourceId));
        version.setStorageKey(storageKey);
        version.setMimeType(mimeType);
        version.setSizeBytes(sizeBytes);
        version.setChecksum(checksum);
        version.setExternalUrl(externalUrl);
        version.setTextBody(textBody);
        version.setScanState("clean");
        version.setUploadedByMembershipId(TenantContext.actorMembershipId());
        version.setNote(note);
        version = versionRepository.save(version);
        resource.setLatestVersion(version.getVersionNo());
        resource.setUpdatedBy(TenantContext.actorUserId());
        resourceRepository.save(resource);
        return toVersionView(version);
    }

    public List<VersionView> listResourceVersions(UUID resourceId) {
        requireResource(resourceId);
        return versionRepository.findByResourceIdOrderByVersionNoDesc(resourceId).stream()
                .map(this::toVersionView).toList();
    }

    /** Restore creates a new version copying the content of an existing one (spec 11.2). */
    public VersionView restoreResourceVersion(UUID resourceId, UUID versionId) {
        requireResource(resourceId);
        ResourceVersion source = versionRepository.findBySchoolIdAndId(schoolId(), versionId)
                .filter(version -> version.getResourceId().equals(resourceId))
                .orElseThrow(() -> ApiException.notFound("version_not_found", "Version not found."));
        return addResourceVersion(resourceId, source.getStorageKey(), source.getMimeType(), source.getSizeBytes(),
                source.getChecksum(), source.getExternalUrl(), source.getTextBody(),
                "Restored from version " + source.getVersionNo());
    }

    // ----- learner side: bookmarks, completions, browsing -----

    public BookmarkView toggleBookmark(UUID resourceId) {
        LearningResource resource = requireResource(resourceId);
        UUID studentId = requireStudentId();
        requireStudentReadable(resource, studentId);
        Optional<ResourceBookmark> existing = bookmarkRepository
                .findBySchoolIdAndResourceIdAndStudentId(schoolId(), resourceId, studentId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return new BookmarkView(false);
        }
        ResourceBookmark bookmark = new ResourceBookmark();
        bookmark.setSchoolId(schoolId());
        bookmark.setResourceId(resourceId);
        bookmark.setStudentId(studentId);
        bookmarkRepository.save(bookmark);
        return new BookmarkView(true);
    }

    public CompletionView markComplete(UUID resourceId) {
        LearningResource resource = requireResource(resourceId);
        UUID studentId = requireStudentId();
        requireStudentReadable(resource, studentId);
        if (completionRepository.findBySchoolIdAndResourceIdAndStudentId(schoolId(), resourceId, studentId).isEmpty()) {
            ResourceCompletion completion = new ResourceCompletion();
            completion.setSchoolId(schoolId());
            completion.setResourceId(resourceId);
            completion.setStudentId(studentId);
            completion.setCompletedAt(Instant.now());
            completionRepository.save(completion);
        }
        return new CompletionView(true);
    }

    public List<LearnerResourceView> listResourcesForMe() {
        UUID studentId = requireStudentId();
        List<LearningResource> visible = resourceRepository
                .findBySchoolIdAndStateAndDeletedAtIsNullOrderByPublishAtDesc(schoolId(), "published").stream()
                .filter(resource -> canStudentRead(studentId, resource))
                .toList();
        Set<UUID> bookmarked = bookmarkRepository.findBySchoolIdAndStudentId(schoolId(), studentId).stream()
                .map(ResourceBookmark::getResourceId).collect(Collectors.toSet());
        Set<UUID> completed = completionRepository.findBySchoolIdAndStudentId(schoolId(), studentId).stream()
                .map(ResourceCompletion::getResourceId).collect(Collectors.toSet());
        List<LearnerResourceView> result = new ArrayList<>();
        for (LearningResource resource : visible) {
            VersionView latest = versionRepository.findByResourceIdOrderByVersionNoDesc(resource.getId()).stream()
                    .findFirst().map(this::toVersionView).orElse(null);
            result.add(new LearnerResourceView(resource.getId(), resource.getTitle(), resource.getDescription(),
                    resource.getResourceType(), resource.getTopic(), resource.getSubjectId(),
                    resource.getClassGroupId(), resource.getPublishAt(), bookmarked.contains(resource.getId()),
                    completed.contains(resource.getId()), latest));
        }
        return result;
    }

    /** A student may read a published resource that is school-wide or scoped to a class they are in. */
    public boolean canStudentRead(UUID studentId, LearningResource resource) {
        if (!"published".equals(resource.getState())) {
            return false;
        }
        if (resource.getClassGroupId() == null) {
            return true;
        }
        return peopleApi.listStudentsByClass(schoolId(), resource.getClassGroupId(), resource.getStreamId()).stream()
                .anyMatch(student -> student.id().equals(studentId));
    }

    // ----- assignments -----

    public PageResponse<AssignmentView> listAssignments(UUID classGroupId, UUID termId, String state, int page,
                                                        int size) {
        Page<Assignment> result = assignmentRepository.search(schoolId(), classGroupId, termId, state,
                pageRequest(page, size));
        return PageResponse.of(result.map(this::toAssignmentView));
    }

    public AssignmentView getAssignment(UUID id) {
        return toAssignmentView(requireAssignment(id));
    }

    public AssignmentView createAssignment(String title, String instructions, UUID subjectId, UUID classGroupId,
                                           UUID streamId, UUID termId, Instant openAt, Instant dueAt,
                                           String latePolicy, BigDecimal maxMarks, Map<String, Object> rubric,
                                           Map<String, Object> audience, List<String> attachmentResourceIds) {
        requireSubject(subjectId);
        requireClass(classGroupId);
        Assignment assignment = new Assignment();
        assignment.setSchoolId(schoolId());
        assignment.setTitle(title);
        assignment.setInstructions(instructions);
        assignment.setSubjectId(subjectId);
        assignment.setClassGroupId(classGroupId);
        assignment.setStreamId(streamId);
        assignment.setTermId(termId);
        assignment.setTeacherMembershipId(TenantContext.actorMembershipId());
        assignment.setOpenAt(openAt);
        assignment.setDueAt(dueAt);
        if (latePolicy != null) {
            validateLatePolicy(latePolicy);
            assignment.setLatePolicy(latePolicy);
        }
        assignment.setMaxMarks(maxMarks == null ? BigDecimal.valueOf(100) : maxMarks);
        assignment.setRubric(rubric);
        assignment.setAudience(audience == null ? new LinkedHashMap<>() : new LinkedHashMap<>(audience));
        assignment.setAttachmentResourceIds(attachmentResourceIds == null ? new ArrayList<>()
                : new ArrayList<>(attachmentResourceIds));
        assignment.setState("draft");
        assignment.setGradesPublished(false);
        assignment.setCreatedBy(TenantContext.actorUserId());
        assignment.setUpdatedBy(TenantContext.actorUserId());
        return toAssignmentView(assignmentRepository.save(assignment));
    }

    public AssignmentView updateAssignment(UUID id, String title, String instructions, UUID subjectId,
                                           UUID classGroupId, UUID streamId, UUID termId, Instant openAt,
                                           Instant dueAt, String latePolicy, BigDecimal maxMarks,
                                           Map<String, Object> rubric, Map<String, Object> audience,
                                           List<String> attachmentResourceIds) {
        Assignment assignment = requireAssignment(id);
        if ("archived".equals(assignment.getState())) {
            throw ApiException.conflict("assignment_archived", "An archived assignment cannot be edited.");
        }
        if (title != null) {
            assignment.setTitle(title);
        }
        if (instructions != null) {
            assignment.setInstructions(instructions);
        }
        if (subjectId != null) {
            requireSubject(subjectId);
            assignment.setSubjectId(subjectId);
        }
        if (classGroupId != null) {
            requireClass(classGroupId);
            assignment.setClassGroupId(classGroupId);
        }
        if (streamId != null) {
            assignment.setStreamId(streamId);
        }
        if (termId != null) {
            assignment.setTermId(termId);
        }
        if (openAt != null) {
            assignment.setOpenAt(openAt);
        }
        if (dueAt != null) {
            assignment.setDueAt(dueAt);
        }
        if (latePolicy != null) {
            validateLatePolicy(latePolicy);
            assignment.setLatePolicy(latePolicy);
        }
        if (maxMarks != null) {
            assignment.setMaxMarks(maxMarks);
        }
        if (rubric != null) {
            assignment.setRubric(rubric);
        }
        if (audience != null) {
            assignment.setAudience(new LinkedHashMap<>(audience));
        }
        if (attachmentResourceIds != null) {
            assignment.setAttachmentResourceIds(new ArrayList<>(attachmentResourceIds));
        }
        assignment.setUpdatedBy(TenantContext.actorUserId());
        return toAssignmentView(assignmentRepository.save(assignment));
    }

    public AssignmentView publishAssignment(UUID id) {
        Assignment assignment = requireAssignment(id);
        if (!"published".equals(assignment.getState())) {
            if ("archived".equals(assignment.getState())) {
                throw ApiException.conflict("assignment_archived", "An archived assignment cannot be published.");
            }
            assignment.setState("published");
            assignment.setUpdatedBy(TenantContext.actorUserId());
            assignment = assignmentRepository.save(assignment);
            eventPublisher.publishEvent(new AssignmentPublishedEvent(
                    schoolId(), assignment.getId(),
                    assignment.getClassGroupId(), assignment.getStreamId(), assignment.getSubjectId(),
                    assignment.getTitle(), assignment.getDueAt()));
        }
        return toAssignmentView(assignment);
    }

    public List<MyAssignmentView> listMyAssignments() {
        UUID studentId = requireStudentId();
        List<Assignment> assignments = assignmentRepository
                .findBySchoolIdAndStateAndDeletedAtIsNullOrderByDueAtAsc(schoolId(), "published");
        List<MyAssignmentView> result = new ArrayList<>();
        for (Assignment assignment : assignments) {
            if (!canStudentRead(studentId, assignment)) {
                continue;
            }
            Submission submission = submissionRepository
                    .findBySchoolIdAndAssignmentIdAndStudentIdAndDeletedAtIsNull(schoolId(), assignment.getId(),
                            studentId)
                    .orElse(null);
            result.add(toMyAssignmentView(assignment, submission));
        }
        return result;
    }

    public boolean canStudentRead(UUID studentId, Assignment assignment) {
        if (!"published".equals(assignment.getState())) {
            return false;
        }
        if (assignment.getClassGroupId() == null) {
            return true;
        }
        return peopleApi.listStudentsByClass(schoolId(), assignment.getClassGroupId(), assignment.getStreamId())
                .stream().anyMatch(student -> student.id().equals(studentId));
    }

    // ----- submissions -----

    public SubmissionView saveDraft(UUID assignmentId, String contentText, List<String> attachmentResourceIds) {
        UUID studentId = requireStudentId();
        Assignment assignment = requireAssignment(assignmentId);
        requireStudentReadable(assignment, studentId);
        Submission submission = submissionRepository
                .findBySchoolIdAndAssignmentIdAndStudentIdAndDeletedAtIsNull(schoolId(), assignmentId, studentId)
                .orElse(null);
        if (submission == null) {
            submission = new Submission();
            submission.setSchoolId(schoolId());
            submission.setAssignmentId(assignmentId);
            submission.setStudentId(studentId);
            submission.setState("draft");
        } else if (!"draft".equals(submission.getState()) && !"returned".equals(submission.getState())) {
            throw ApiException.conflict("already_submitted", "The submission has already been submitted.");
        }
        submission.setContentText(contentText);
        submission.setAttachmentResourceIds(attachmentResourceIds == null ? new ArrayList<>()
                : new ArrayList<>(attachmentResourceIds));
        submission.setUpdatedBy(TenantContext.actorUserId());
        return toLearnerSubmissionView(submissionRepository.save(submission), assignment);
    }

    public SubmissionView submit(UUID assignmentId, String contentText, List<String> attachmentResourceIds,
                                 String idempotencyKey) {
        UUID studentId = requireStudentId();
        Assignment assignment = requireAssignment(assignmentId);
        requireStudentReadable(assignment, studentId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Submission> replay = submissionRepository
                    .findBySchoolIdAndIdempotencyKeyAndDeletedAtIsNull(schoolId(), idempotencyKey);
            if (replay.isPresent()) {
                Submission existing = replay.get();
                if (!existing.getStudentId().equals(studentId) || !existing.getAssignmentId().equals(assignmentId)) {
                    throw ApiException.conflict("idempotency_key_reused",
                            "The idempotency key was already used for another submission.");
                }
                return toLearnerSubmissionView(existing, assignment);
            }
        }
        Submission submission = submissionRepository
                .findBySchoolIdAndAssignmentIdAndStudentIdAndDeletedAtIsNull(schoolId(), assignmentId, studentId)
                .orElse(null);
        if (submission == null) {
            submission = new Submission();
            submission.setSchoolId(schoolId());
            submission.setAssignmentId(assignmentId);
            submission.setStudentId(studentId);
            submission.setState("submitted");
        } else {
            switch (submission.getState()) {
                case "draft" -> submission.setState("submitted");
                case "returned" -> submission.setState("resubmitted");
                default -> throw ApiException.conflict("already_submitted",
                        "The submission has already been submitted.");
            }
        }
        submission.setContentText(contentText);
        submission.setAttachmentResourceIds(attachmentResourceIds == null ? new ArrayList<>()
                : new ArrayList<>(attachmentResourceIds));
        submission.setSubmittedAt(Instant.now());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            submission.setIdempotencyKey(idempotencyKey);
        }
        submission.setUpdatedBy(TenantContext.actorUserId());
        return toLearnerSubmissionView(submissionRepository.save(submission), assignment);
    }

    public SubmissionView getMySubmission(UUID assignmentId) {
        UUID studentId = requireStudentId();
        Assignment assignment = requireAssignment(assignmentId);
        Submission submission = submissionRepository
                .findBySchoolIdAndAssignmentIdAndStudentIdAndDeletedAtIsNull(schoolId(), assignmentId, studentId)
                .orElseThrow(() -> ApiException.notFound("submission_not_found", "No submission for this assignment."));
        return toLearnerSubmissionView(submission, assignment);
    }

    // ----- grading (teacher side) -----

    public List<SubmissionView> listSubmissions(UUID assignmentId) {
        requireAssignment(assignmentId);
        return submissionRepository.findBySchoolIdAndAssignmentIdAndDeletedAtIsNull(schoolId(), assignmentId).stream()
                .map(this::toTeacherSubmissionView).toList();
    }

    public SubmissionView grade(UUID submissionId, BigDecimal marks, String feedback) {
        Submission submission = requireSubmission(submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        if ("draft".equals(submission.getState())) {
            throw ApiException.conflict("not_submitted", "Draft submissions cannot be graded.");
        }
        if (marks == null) {
            throw ApiException.badRequest("marks_required", "Marks are required.");
        }
        if (marks.compareTo(assignment.getMaxMarks()) > 0) {
            throw ApiException.badRequest("marks_exceed_max", "Marks exceed the maximum for this assignment.");
        }
        submission.setGrade(marks);
        submission.setFeedback(feedback);
        submission.setGradedByMembershipId(TenantContext.actorMembershipId());
        submission.setGradedAt(Instant.now());
        submission.setState("graded");
        submission.setUpdatedBy(TenantContext.actorUserId());
        return toTeacherSubmissionView(submissionRepository.save(submission));
    }

    public AssignmentView publishGrades(UUID assignmentId) {
        Assignment assignment = requireAssignment(assignmentId);
        boolean before = assignment.isGradesPublished();
        assignment.setGradesPublished(true);
        assignment.setUpdatedBy(TenantContext.actorUserId());
        assignment = assignmentRepository.save(assignment);
        auditLogService.record("assignment.publish_grades", "assignment", assignment.getId(),
                Map.of("gradesPublished", before), Map.of("gradesPublished", true));
        return toAssignmentView(assignment);
    }

    public SubmissionView reopen(UUID submissionId) {
        Submission submission = requireSubmission(submissionId);
        if ("draft".equals(submission.getState())) {
            throw ApiException.conflict("not_submitted", "A draft submission cannot be reopened.");
        }
        String before = submission.getState();
        submission.setState("returned");
        submission.setReopenedAt(Instant.now());
        submission.setUpdatedBy(TenantContext.actorUserId());
        submission = submissionRepository.save(submission);
        auditLogService.record("submission.reopen", "submission", submission.getId(),
                Map.of("state", before), Map.of("state", "returned"));
        return toTeacherSubmissionView(submission);
    }

    public List<GradeRowView> listGrades(UUID assignmentId) {
        requireAssignment(assignmentId);
        List<Submission> submissions = submissionRepository
                .findBySchoolIdAndAssignmentIdAndDeletedAtIsNull(schoolId(), assignmentId);
        Map<UUID, String> names = studentNames(submissions.stream().map(Submission::getStudentId).distinct().toList());
        return submissions.stream().map(submission -> new GradeRowView(submission.getStudentId(),
                names.get(submission.getStudentId()), submission.getState(), submission.getGrade(),
                submission.getFeedback(), submission.getSubmittedAt(), submission.getGradedAt())).toList();
    }

    /** Students see their own published grades; parents see published grades of children they may view. */
    public List<LearnerGradeView> learnerGradeView() {
        UUID userId = SecurityUtils.requireUserId();
        List<LearnerGradeView> result = new ArrayList<>();
        Optional<PeopleApi.StudentRef> student = peopleApi.studentForUser(schoolId(), userId);
        if (student.isPresent()) {
            appendPublishedGrades(result, student.get(), null);
            return result;
        }
        for (PeopleApi.ChildRef child : peopleApi.childrenOfParent(schoolId(), userId)) {
            if (peopleApi.parentCanView(schoolId(), userId, child.student().id(), "grades")) {
                appendPublishedGrades(result, child.student(), child.relationship());
            }
        }
        return result;
    }

    // ----- helpers -----

    private void appendPublishedGrades(List<LearnerGradeView> result, PeopleApi.StudentRef student,
                                       String relationship) {
        for (Submission submission : submissionRepository
                .findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId(), student.id())) {
            Assignment assignment = assignmentRepository
                    .findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), submission.getAssignmentId()).orElse(null);
            if (assignment == null || !assignment.isGradesPublished() || submission.getGrade() == null) {
                continue;
            }
            result.add(new LearnerGradeView(student.id(), student.fullName(), relationship, assignment.getId(),
                    assignment.getTitle(), submission.getGrade(), assignment.getMaxMarks(), submission.getFeedback(),
                    submission.getGradedAt()));
        }
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private UUID requireStudentId() {
        return peopleApi.studentForUser(schoolId(), SecurityUtils.requireUserId())
                .map(PeopleApi.StudentRef::id)
                .orElseThrow(() -> ApiException.forbidden("not_a_student",
                        "This action is only available to students."));
    }

    private LearningResource requireResource(UUID id) {
        return resourceRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("resource_not_found", "Resource not found."));
    }

    private Assignment requireAssignment(UUID id) {
        return assignmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("assignment_not_found", "Assignment not found."));
    }

    private Submission requireSubmission(UUID id) {
        return submissionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("submission_not_found", "Submission not found."));
    }

    private void requireStudentReadable(LearningResource resource, UUID studentId) {
        if (!canStudentRead(studentId, resource)) {
            throw ApiException.forbidden("resource_not_available", "This resource is not available to you.");
        }
    }

    private void requireStudentReadable(Assignment assignment, UUID studentId) {
        if (!canStudentRead(studentId, assignment)) {
            throw ApiException.forbidden("assignment_not_available", "This assignment is not available to you.");
        }
    }

    private void validateResourceType(String resourceType) {
        if (resourceType == null || !RESOURCE_TYPES.contains(resourceType)) {
            throw ApiException.badRequest("invalid_resource_type", "Unknown resource type.");
        }
    }

    private void validateLatePolicy(String latePolicy) {
        if (!LATE_POLICIES.contains(latePolicy)) {
            throw ApiException.badRequest("invalid_late_policy", "Unknown late policy.");
        }
    }

    private void validateScope(UUID subjectId, UUID classGroupId) {
        if (subjectId != null) {
            requireSubject(subjectId);
        }
        if (classGroupId != null) {
            requireClass(classGroupId);
        }
    }

    private void requireSubject(UUID subjectId) {
        if (subjectId == null || academicsApi.findSubject(schoolId(), subjectId).isEmpty()) {
            throw ApiException.badRequest("subject_not_found", "Subject not found.");
        }
    }

    private void requireClass(UUID classGroupId) {
        if (classGroupId == null || academicsApi.findClass(schoolId(), classGroupId).isEmpty()) {
            throw ApiException.badRequest("class_not_found", "Class not found.");
        }
    }

    private int nextVersionNo(UUID resourceId) {
        return versionRepository.findByResourceIdOrderByVersionNoDesc(resourceId).stream().findFirst()
                .map(version -> version.getVersionNo() + 1).orElse(1);
    }

    private Map<UUID, String> studentNames(List<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return peopleApi.findStudents(schoolId(), studentIds).stream()
                .collect(Collectors.toMap(PeopleApi.StudentRef::id, PeopleApi.StudentRef::fullName));
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private void softDelete(TenantEntity entity) {
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(TenantContext.actorUserId());
    }

    private ResourceView toResourceView(LearningResource resource) {
        return new ResourceView(resource.getId(), resource.getTitle(), resource.getDescription(),
                resource.getResourceType(), resource.getTopic(), resource.getSubjectId(), resource.getClassGroupId(),
                resource.getStreamId(), resource.getAcademicYearId(), resource.getTermId(), resource.getState(),
                resource.getPublishAt(), resource.getLatestVersion(), resource.getAudience(),
                resource.getUploadedByMembershipId());
    }

    private VersionView toVersionView(ResourceVersion version) {
        return new VersionView(version.getId(), version.getResourceId(), version.getVersionNo(), version.getStorageKey(),
                version.getMimeType(), version.getSizeBytes(), version.getChecksum(), version.getExternalUrl(),
                version.getTextBody(), version.getScanState(), version.getUploadedByMembershipId(), version.getNote(),
                version.getCreatedAt());
    }

    private AssignmentView toAssignmentView(Assignment assignment) {
        return new AssignmentView(assignment.getId(), assignment.getTitle(), assignment.getInstructions(),
                assignment.getSubjectId(), assignment.getClassGroupId(), assignment.getStreamId(), assignment.getTermId(),
                assignment.getTeacherMembershipId(), assignment.getOpenAt(), assignment.getDueAt(),
                assignment.getLatePolicy(), assignment.getMaxMarks(), assignment.getRubric(), assignment.getAudience(),
                assignment.getAttachmentResourceIds(), assignment.isGradesPublished(), assignment.getState());
    }

    private MyAssignmentView toMyAssignmentView(Assignment assignment, Submission submission) {
        boolean published = assignment.isGradesPublished();
        return new MyAssignmentView(assignment.getId(), assignment.getTitle(), assignment.getInstructions(),
                assignment.getSubjectId(), assignment.getClassGroupId(), assignment.getOpenAt(),
                assignment.getDueAt(), assignment.getLatePolicy(), assignment.getMaxMarks(), published,
                submission == null ? "not_started" : submission.getState(),
                submission == null ? null : submission.getSubmittedAt(),
                published && submission != null ? submission.getGrade() : null,
                published && submission != null ? submission.getFeedback() : null);
    }

    private SubmissionView toTeacherSubmissionView(Submission submission) {
        return toSubmissionView(submission, true);
    }

    private SubmissionView toLearnerSubmissionView(Submission submission, Assignment assignment) {
        return toSubmissionView(submission, assignment.isGradesPublished());
    }

    private SubmissionView toSubmissionView(Submission submission, boolean includeGrade) {
        return new SubmissionView(submission.getId(), submission.getAssignmentId(), submission.getStudentId(),
                submission.getState(), submission.getContentText(), submission.getAttachmentResourceIds(),
                submission.getSubmittedAt(), includeGrade ? submission.getGrade() : null,
                includeGrade ? submission.getFeedback() : null, includeGrade ? submission.getGradedAt() : null,
                submission.getReopenedAt());
    }

    public record ResourceView(UUID id, String title, String description, String resourceType, String topic,
                               UUID subjectId, UUID classGroupId, UUID streamId, UUID academicYearId, UUID termId,
                               String state, Instant publishAt, int latestVersion, Map<String, Object> audience,
                               UUID uploadedByMembershipId) {
    }

    public record VersionView(UUID id, UUID resourceId, int versionNo, String storageKey, String mimeType,
                              Long sizeBytes, String checksum, String externalUrl, String textBody, String scanState,
                              UUID uploadedByMembershipId, String note, Instant createdAt) {
    }

    public record LearnerResourceView(UUID id, String title, String description, String resourceType, String topic,
                                      UUID subjectId, UUID classGroupId, Instant publishAt, boolean bookmarked,
                                      boolean completed, VersionView latestVersion) {
    }

    public record BookmarkView(boolean bookmarked) {
    }

    public record CompletionView(boolean completed) {
    }

    public record AssignmentView(UUID id, String title, String instructions, UUID subjectId, UUID classGroupId,
                                 UUID streamId, UUID termId, UUID teacherMembershipId, Instant openAt, Instant dueAt,
                                 String latePolicy, BigDecimal maxMarks, Map<String, Object> rubric,
                                 Map<String, Object> audience, List<String> attachmentResourceIds,
                                 boolean gradesPublished, String state) {
    }

    public record MyAssignmentView(UUID id, String title, String instructions, UUID subjectId, UUID classGroupId,
                                   Instant openAt, Instant dueAt, String latePolicy, BigDecimal maxMarks,
                                   boolean gradesPublished, String submissionState, Instant submittedAt,
                                   BigDecimal grade, String feedback) {
    }

    public record SubmissionView(UUID id, UUID assignmentId, UUID studentId, String state, String contentText,
                                 List<String> attachmentResourceIds, Instant submittedAt, BigDecimal grade,
                                 String feedback, Instant gradedAt, Instant reopenedAt) {
    }

    public record GradeRowView(UUID studentId, String studentName, String state, BigDecimal grade, String feedback,
                               Instant submittedAt, Instant gradedAt) {
    }

    public record LearnerGradeView(UUID studentId, String studentName, String relationship, UUID assignmentId,
                                   String assignmentTitle, BigDecimal grade, BigDecimal maxMarks, String feedback,
                                   Instant gradedAt) {
    }
}
