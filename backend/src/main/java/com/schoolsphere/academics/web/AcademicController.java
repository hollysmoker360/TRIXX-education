package com.schoolsphere.academics.web;

import com.schoolsphere.academics.internal.AcademicService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/academics")
public class AcademicController {

    private final AcademicService academicService;

    public AcademicController(AcademicService academicService) {
        this.academicService = academicService;
    }

    // ----- years -----

    @GetMapping("/years")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.YearView> years() {
        return academicService.listYears();
    }

    @PostMapping("/years")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.YearView createYear(@Valid @RequestBody YearRequest request) {
        return academicService.createYear(request.name(), request.startsOn(), request.endsOn(),
                Boolean.TRUE.equals(request.active()));
    }

    @PatchMapping("/years/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.YearView updateYear(@PathVariable UUID id, @RequestBody YearRequest request) {
        return academicService.updateYear(id, request.name(), request.startsOn(), request.endsOn());
    }

    @PostMapping("/years/{id}/activate")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.YearView activateYear(@PathVariable UUID id) {
        return academicService.setActiveYear(id);
    }

    @DeleteMapping("/years/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteYear(@PathVariable UUID id) {
        academicService.deleteYear(id);
    }

    // ----- terms -----

    @GetMapping("/terms")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.TermView> terms(@RequestParam(required = false) UUID academicYearId) {
        return academicService.listTerms(academicYearId);
    }

    @PostMapping("/terms")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.TermView createTerm(@Valid @RequestBody TermRequest request) {
        return academicService.createTerm(request.academicYearId(), request.name(), request.seq(),
                request.startsOn(), request.endsOn(), Boolean.TRUE.equals(request.current()));
    }

    @PostMapping("/terms/{id}/activate")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.TermView activateTerm(@PathVariable UUID id) {
        return academicService.setCurrentTerm(id);
    }

    @DeleteMapping("/terms/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteTerm(@PathVariable UUID id) {
        academicService.deleteTerm(id);
    }

    // ----- departments -----

    @GetMapping("/departments")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.DepartmentView> departments() {
        return academicService.listDepartments();
    }

    @PostMapping("/departments")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.DepartmentView createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return academicService.createDepartment(request.name(), request.code(), request.headMembershipId());
    }

    @PatchMapping("/departments/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.DepartmentView updateDepartment(@PathVariable UUID id,
                                                           @RequestBody DepartmentRequest request) {
        return academicService.updateDepartment(id, request.name(), request.code(), request.headMembershipId());
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteDepartment(@PathVariable UUID id) {
        academicService.deleteDepartment(id);
    }

    // ----- classes -----

    @GetMapping("/classes")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.ClassView> classes() {
        return academicService.listClasses();
    }

    @PostMapping("/classes")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.ClassView createClass(@Valid @RequestBody ClassRequest request) {
        return academicService.createClass(request.name(), request.level(), request.departmentId(),
                request.classTeacherMembershipId(), request.capacity());
    }

    @PatchMapping("/classes/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.ClassView updateClass(@PathVariable UUID id, @RequestBody ClassRequest request) {
        return academicService.updateClass(id, request.name(), request.level(), request.departmentId(),
                request.capacity());
    }

    @DeleteMapping("/classes/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteClass(@PathVariable UUID id) {
        academicService.deleteClass(id);
    }

    // ----- streams -----

    @GetMapping("/streams")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.StreamView> streams(@RequestParam(required = false) UUID classGroupId) {
        return academicService.listStreams(classGroupId);
    }

    @PostMapping("/streams")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.StreamView createStream(@Valid @RequestBody StreamRequest request) {
        return academicService.createStream(request.classGroupId(), request.name());
    }

    @DeleteMapping("/streams/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteStream(@PathVariable UUID id) {
        academicService.deleteStream(id);
    }

    // ----- subjects -----

    @GetMapping("/subjects")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.SubjectView> subjects() {
        return academicService.listSubjects();
    }

    @PostMapping("/subjects")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.SubjectView createSubject(@Valid @RequestBody SubjectRequest request) {
        return academicService.createSubject(request.name(), request.code(), request.departmentId(),
                Boolean.TRUE.equals(request.core()));
    }

    @PatchMapping("/subjects/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.SubjectView updateSubject(@PathVariable UUID id, @RequestBody SubjectRequest request) {
        return academicService.updateSubject(id, request.name(), request.code(), request.departmentId(),
                request.core());
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteSubject(@PathVariable UUID id) {
        academicService.deleteSubject(id);
    }

    // ----- classrooms -----

    @GetMapping("/classrooms")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.RoomView> classrooms() {
        return academicService.listClassrooms();
    }

    @PostMapping("/classrooms")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.RoomView createClassroom(@Valid @RequestBody ClassroomRequest request) {
        return academicService.createClassroom(request.name(), request.roomType(), request.capacity());
    }

    @DeleteMapping("/classrooms/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deleteClassroom(@PathVariable UUID id) {
        academicService.deleteClassroom(id);
    }

    // ----- periods -----

    @GetMapping("/periods")
    @PreAuthorize("@policy.can('academics.view')")
    public List<AcademicService.PeriodView> periods(@RequestParam(required = false) UUID academicYearId) {
        return academicService.listPeriods(academicYearId);
    }

    @PostMapping("/periods")
    @PreAuthorize("@policy.can('academics.manage')")
    public AcademicService.PeriodView createPeriod(@Valid @RequestBody PeriodRequest request) {
        return academicService.createPeriod(request.academicYearId(), request.name(), request.seq(),
                request.startsAt(), request.endsAt(), Boolean.TRUE.equals(request.isBreak()));
    }

    @DeleteMapping("/periods/{id}")
    @PreAuthorize("@policy.can('academics.manage')")
    public void deletePeriod(@PathVariable UUID id) {
        academicService.deletePeriod(id);
    }

    public record YearRequest(@NotBlank String name, LocalDate startsOn, LocalDate endsOn, Boolean active) {
    }

    public record TermRequest(UUID academicYearId, @NotBlank String name, int seq, LocalDate startsOn,
                              LocalDate endsOn, Boolean current) {
    }

    public record DepartmentRequest(@NotBlank String name, String code, UUID headMembershipId) {
    }

    public record ClassRequest(@NotBlank String name, Integer level, UUID departmentId,
                               UUID classTeacherMembershipId, Integer capacity) {
    }

    public record StreamRequest(UUID classGroupId, @NotBlank String name) {
    }

    public record SubjectRequest(@NotBlank String name, String code, UUID departmentId, Boolean core) {
    }

    public record ClassroomRequest(@NotBlank String name, String roomType, Integer capacity) {
    }

    public record PeriodRequest(UUID academicYearId, @NotBlank String name, int seq, LocalTime startsAt,
                                LocalTime endsAt, Boolean isBreak) {
    }
}
