package com.schoolsphere.timetable.web;

import com.schoolsphere.timetable.internal.LessonScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dated lesson occurrences and their reminders (spec 12.2). */
@RestController
@RequestMapping("/api/v1/timetable")
public class LessonScheduleController {

    private final LessonScheduleService lessonScheduleService;

    public LessonScheduleController(LessonScheduleService lessonScheduleService) {
        this.lessonScheduleService = lessonScheduleService;
    }

    /** The lessons that actually happen between two dates, for a class or a teacher's day view. */
    @GetMapping("/occurrences")
    @PreAuthorize("@policy.can('timetable.view')")
    public List<LessonScheduleService.OccurrenceView> list(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) UUID teacherMembershipId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return lessonScheduleService.list(classGroupId, teacherMembershipId, from, to);
    }

    /** Materialises an explicit window of a version, for staff preparing a future term. */
    @PostMapping("/versions/{id}/schedule")
    @PreAuthorize("@policy.can('timetable.publish')")
    public ScheduleResult schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest request) {
        return new ScheduleResult(lessonScheduleService.materialise(id, request.from(), request.to()));
    }

    @PostMapping("/versions/{id}/schedule/roll-forward")
    @PreAuthorize("@policy.can('timetable.publish')")
    public ScheduleResult rollForward(@PathVariable UUID id) {
        return new ScheduleResult(lessonScheduleService.rollForward(id));
    }

    public record ScheduleRequest(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    }

    public record ScheduleResult(int occurrences) {
    }
}
