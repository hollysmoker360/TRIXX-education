package com.schoolsphere.people.web;

import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.people.internal.CsvImportService;
import com.schoolsphere.people.internal.PeopleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {

    private final PeopleService peopleService;
    private final CsvImportService csvImportService;

    public PeopleController(PeopleService peopleService, CsvImportService csvImportService) {
        this.peopleService = peopleService;
        this.csvImportService = csvImportService;
    }

    // ----- students -----

    @GetMapping("/students")
    @PreAuthorize("@policy.can('people.view')")
    public PageResponse<PeopleService.StudentView> students(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "25") int size,
                                                            @RequestParam(required = false) String q,
                                                            @RequestParam(required = false) UUID classGroupId,
                                                            @RequestParam(required = false) UUID streamId) {
        return peopleService.listStudents(page, size, q, classGroupId, streamId);
    }

    @PostMapping("/students")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.StudentView createStudent(@Valid @RequestBody StudentRequest request) {
        return peopleService.createStudent(request.userId(), request.admissionNumber(), request.firstName(),
                request.lastName(), request.otherNames(), request.dateOfBirth(), request.gender(), request.upi(),
                request.medicalNotes());
    }

    @GetMapping("/students/{id}")
    @PreAuthorize("@policy.can('people.view')")
    public PeopleService.StudentDetailView student(@PathVariable UUID id) {
        return peopleService.getStudent(id);
    }

    @PatchMapping("/students/{id}")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.StudentView updateStudent(@PathVariable UUID id, @RequestBody StudentRequest request) {
        return peopleService.updateStudent(id, request.admissionNumber(), request.firstName(), request.lastName(),
                request.otherNames(), request.dateOfBirth(), request.gender(), request.upi(), request.medicalNotes(),
                request.status());
    }

    @DeleteMapping("/students/{id}")
    @PreAuthorize("@policy.can('people.manage')")
    public void deleteStudent(@PathVariable UUID id) {
        peopleService.deleteStudent(id);
    }

    @PostMapping("/students/import")
    @PreAuthorize("@policy.can('people.manage')")
    public CsvImportService.ImportReport importStudents(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(defaultValue = "false") boolean dryRun) {
        try {
            return csvImportService.importStudents(file.getBytes(), dryRun);
        } catch (IOException e) {
            throw ApiException.badRequest("unreadable_file", "The uploaded file could not be read.");
        }
    }

    // ----- teachers -----

    @GetMapping("/teachers")
    @PreAuthorize("@policy.can('people.view')")
    public List<PeopleService.TeacherView> teachers() {
        return peopleService.listTeachers();
    }

    @PostMapping("/teachers")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.OnboardedTeacher createTeacher(@Valid @RequestBody TeacherRequest request) {
        return peopleService.onboardTeacher(request.userId(), request.email(), request.staffNumber(),
                request.firstName(), request.lastName(), request.phone(), request.maxWeeklyPeriods());
    }

    @PatchMapping("/teachers/{id}")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.TeacherView updateTeacher(@PathVariable UUID id, @RequestBody TeacherRequest request) {
        return peopleService.updateTeacher(id, request.staffNumber(), request.firstName(), request.lastName(),
                request.phone(), request.maxWeeklyPeriods());
    }

    // ----- parents -----

    @GetMapping("/parents")
    @PreAuthorize("@policy.can('people.view')")
    public List<PeopleService.ParentView> parents() {
        return peopleService.listParents();
    }

    @PostMapping("/parents")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.OnboardedParent createParent(@Valid @RequestBody ParentRequest request) {
        return peopleService.onboardParent(request.userId(), request.email(), request.firstName(), request.lastName(),
                request.phone());
    }

    @PostMapping("/parents/invite")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.ParentInviteView inviteParent(@Valid @RequestBody InviteParentRequest request) {
        return peopleService.inviteParent(request.email(), request.firstName(), request.lastName(), request.phone(),
                request.childStudentId());
    }

    // ----- guardian links -----

    @GetMapping("/guardians")
    @PreAuthorize("@policy.can('people.view')")
    public List<PeopleService.GuardianView> guardians(@RequestParam UUID studentId) {
        return peopleService.listGuardians(studentId);
    }

    @PostMapping("/guardians")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.GuardianView createGuardian(@Valid @RequestBody GuardianLinkRequest request) {
        return peopleService.linkGuardian(request.parentId(), request.studentId(), request.relationship(),
                request.primary(), request.visibilities());
    }

    @PatchMapping("/guardians/{id}")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.GuardianView updateGuardian(@PathVariable UUID id,
                                                     @RequestBody GuardianPatchRequest request) {
        return peopleService.updateGuardian(id, request.visibilities(), request.primary());
    }

    // ----- enrollments -----

    @GetMapping("/enrollments")
    @PreAuthorize("@policy.can('people.view')")
    public List<PeopleService.EnrollmentView> enrollments(@RequestParam(required = false) UUID academicYearId,
                                                          @RequestParam(required = false) UUID classGroupId) {
        return peopleService.listEnrollments(academicYearId, classGroupId);
    }

    @PostMapping("/enrollments")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.EnrollmentView createEnrollment(@Valid @RequestBody EnrollmentRequest request) {
        return peopleService.createEnrollment(request.studentId(), request.academicYearId(), request.classGroupId(),
                request.streamId(), request.status());
    }

    @PostMapping("/enrollments/{id}/transition")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.EnrollmentView transitionEnrollment(@PathVariable UUID id,
                                                             @Valid @RequestBody EnrollmentTransitionRequest request) {
        return peopleService.transitionEnrollment(id, request.toStatus(), request.reason());
    }

    // ----- teacher assignments -----

    @GetMapping("/teacher-assignments")
    @PreAuthorize("@policy.can('people.view')")
    public List<PeopleService.AssignmentView> teacherAssignments(@RequestParam(required = false) UUID termId,
                                                                 @RequestParam(required = false) UUID teacherId) {
        return peopleService.listTeacherAssignments(termId, teacherId);
    }

    @PostMapping("/teacher-assignments")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.AssignmentView createTeacherAssignment(@Valid @RequestBody TeacherAssignmentRequest request) {
        return peopleService.createTeacherAssignment(request.teacherId(), request.classGroupId(), request.streamId(),
                request.subjectId(), request.termId(), request.periodsPerWeek());
    }

    @DeleteMapping("/teacher-assignments/{id}")
    @PreAuthorize("@policy.can('people.manage')")
    public void deleteTeacherAssignment(@PathVariable UUID id) {
        peopleService.deleteTeacherAssignment(id);
    }

    // ----- grading scales -----

    @GetMapping("/grading-scales")
    @PreAuthorize("@policy.can('people.view')")
    public List<PeopleService.GradingScaleView> gradingScales() {
        return peopleService.listGradingScales();
    }

    @PostMapping("/grading-scales")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.GradingScaleView createGradingScale(@Valid @RequestBody GradingScaleRequest request) {
        return peopleService.createGradingScale(request.name(), request.kind(), request.bands(), request.isDefault());
    }

    @PostMapping("/grading-scales/defaults")
    @PreAuthorize("@policy.can('people.manage')")
    public List<PeopleService.GradingScaleView> seedGradingScales() {
        return peopleService.seedDefaults();
    }

    @PatchMapping("/grading-scales/{id}")
    @PreAuthorize("@policy.can('people.manage')")
    public PeopleService.GradingScaleView updateGradingScale(@PathVariable UUID id,
                                                             @RequestBody GradingScaleRequest request) {
        return peopleService.updateGradingScale(id, request.name(), request.bands(), request.isDefault());
    }

    // ----- self-service -----

    @GetMapping("/me/student")
    public PeopleService.StudentView myStudent() {
        return peopleService.myStudent();
    }

    @GetMapping("/me/student-detail")
    public PeopleService.StudentDetailView myStudentDetail() {
        return peopleService.myStudentDetail();
    }

    @GetMapping("/me/children")
    public List<PeopleService.ChildView> myChildren() {
        return peopleService.myChildren();
    }

    public record StudentRequest(UUID userId, String admissionNumber, @NotBlank String firstName,
                                 @NotBlank String lastName, String otherNames, LocalDate dateOfBirth, String gender,
                                 String upi, String medicalNotes, String status) {
    }

    public record TeacherRequest(UUID userId, String email, String staffNumber, @NotBlank String firstName,
                                 @NotBlank String lastName, String phone, Integer maxWeeklyPeriods) {
    }

    public record ParentRequest(UUID userId, String email, @NotBlank String firstName, @NotBlank String lastName,
                                String phone) {
    }

    public record InviteParentRequest(@NotBlank String email, @NotBlank String firstName, @NotBlank String lastName,
                                     String phone, UUID childStudentId) {
    }

    public record GuardianLinkRequest(@NotNull UUID parentId, @NotNull UUID studentId, @NotBlank String relationship,
                                      Boolean primary, Map<String, Boolean> visibilities) {
    }

    public record GuardianPatchRequest(Map<String, Boolean> visibilities, Boolean primary) {
    }

    public record EnrollmentRequest(@NotNull UUID studentId, @NotNull UUID academicYearId, @NotNull UUID classGroupId,
                                    UUID streamId, String status) {
    }

    public record EnrollmentTransitionRequest(@NotBlank String toStatus, String reason) {
    }

    public record TeacherAssignmentRequest(@NotNull UUID teacherId, @NotNull UUID classGroupId, UUID streamId,
                                           @NotNull UUID subjectId, @NotNull UUID termId, Integer periodsPerWeek) {
    }

    public record GradingScaleRequest(@NotBlank String name, String kind, List<Map<String, Object>> bands,
                                      Boolean isDefault) {
    }
}
