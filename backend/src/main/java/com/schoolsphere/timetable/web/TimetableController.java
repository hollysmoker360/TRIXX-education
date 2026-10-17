package com.schoolsphere.timetable.web;

import com.schoolsphere.timetable.internal.TimetableService;
import jakarta.validation.Valid;
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

/** Timetable versions, entries, validation and generation (spec 12.1). */
@RestController
@RequestMapping("/api/v1/timetable")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping("/versions")
    @PreAuthorize("@policy.can('timetable.view')")
    public List<TimetableService.VersionSummary> listVersions(@RequestParam(required = false) UUID termId) {
        return timetableService.listVersions(termId);
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("@policy.can('timetable.view')")
    public TimetableService.VersionView getVersion(@PathVariable UUID id) {
        return timetableService.getVersion(id);
    }

    @PostMapping("/versions")
    @PreAuthorize("@policy.can('timetable.edit')")
    public TimetableService.VersionView createVersion(@Valid @RequestBody VersionRequest request) {
        return timetableService.createVersion(request.academicYearId(), request.termId(), request.name(),
                request.note());
    }

    @PatchMapping("/versions/{id}")
    @PreAuthorize("@policy.can('timetable.edit')")
    public TimetableService.VersionView updateVersion(@PathVariable UUID id,
                                                      @RequestBody VersionRequest request) {
        return timetableService.updateVersion(id, request.name(), request.note());
    }

    @DeleteMapping("/versions/{id}")
    @PreAuthorize("@policy.can('timetable.edit')")
    public void deleteVersion(@PathVariable UUID id) {
        timetableService.deleteVersion(id);
    }

    @PostMapping("/versions/{id}/copy")
    @PreAuthorize("@policy.can('timetable.edit')")
    public TimetableService.VersionView copyVersion(@PathVariable UUID id,
                                                    @RequestBody(required = false) VersionRequest request) {
        return timetableService.copyVersion(id, request == null ? null : request.name());
    }

    @PostMapping("/versions/{id}/validate")
    @PreAuthorize("@policy.can('timetable.view')")
    public TimetableService.ValidationView validate(@PathVariable UUID id) {
        return timetableService.validateVersion(id);
    }

    @PostMapping("/versions/{id}/generate")
    @PreAuthorize("@policy.can('timetable.edit')")
    public TimetableService.VersionView generate(@PathVariable UUID id,
                                                 @RequestBody(required = false) GenerateRequest request) {
        return timetableService.generate(id,
                new TimetableService.GenerateInput(request == null ? null : request.seed(),
                        request == null ? null : request.attempts(),
                        request == null ? null : request.days()));
    }

    @PostMapping("/versions/{id}/publish")
    @PreAuthorize("@policy.can('timetable.publish')")
    public TimetableService.VersionView publish(@PathVariable UUID id) {
        return timetableService.publish(id);
    }

    @PostMapping("/versions/{id}/archive")
    @PreAuthorize("@policy.can('timetable.publish')")
    public TimetableService.VersionView archive(@PathVariable UUID id) {
        return timetableService.archive(id);
    }

    @GetMapping("/versions/{id}/entries")
    @PreAuthorize("@policy.can('timetable.view')")
    public List<TimetableService.EntryView> listEntries(@PathVariable UUID id,
                                                        @RequestParam(required = false) UUID classGroupId,
                                                        @RequestParam(required = false) UUID teacherMembershipId) {
        return timetableService.listEntries(id, classGroupId, teacherMembershipId);
    }

    @PostMapping("/versions/{id}/entries")
    @PreAuthorize("@policy.can('timetable.edit')")
    public TimetableService.EntryView addEntry(@PathVariable UUID id,
                                               @Valid @RequestBody EntryRequest request) {
        return timetableService.addEntry(id, request.toInput());
    }

    @PatchMapping("/entries/{entryId}")
    @PreAuthorize("@policy.can('timetable.edit')")
    public TimetableService.EntryView updateEntry(@PathVariable UUID entryId,
                                                  @Valid @RequestBody EntryRequest request) {
        return timetableService.updateEntry(entryId, request.toInput());
    }

    @DeleteMapping("/entries/{entryId}")
    @PreAuthorize("@policy.can('timetable.edit')")
    public void deleteEntry(@PathVariable UUID entryId) {
        timetableService.deleteEntry(entryId);
    }

    @GetMapping("/published")
    @PreAuthorize("@policy.can('timetable.view')")
    public TimetableService.PublishedView published(@RequestParam(required = false) UUID termId,
                                                    @RequestParam(required = false) UUID classGroupId,
                                                    @RequestParam(required = false) UUID streamId,
                                                    @RequestParam(required = false) UUID teacherMembershipId) {
        return timetableService.getPublished(termId, classGroupId, streamId, teacherMembershipId);
    }

    public record VersionRequest(UUID academicYearId, UUID termId, String name, String note) {
    }

    public record GenerateRequest(Long seed, Integer attempts, List<Integer> days) {
    }

    public record EntryRequest(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                               UUID classroomId, Integer dayOfWeek, Integer periodSeq, Integer span,
                               Boolean locked) {

        TimetableService.EntryInput toInput() {
            return new TimetableService.EntryInput(classGroupId, streamId, subjectId, teacherMembershipId,
                    classroomId, dayOfWeek, periodSeq, span, locked);
        }
    }
}
