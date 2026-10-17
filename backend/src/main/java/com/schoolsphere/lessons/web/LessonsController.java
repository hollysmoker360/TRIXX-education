package com.schoolsphere.lessons.web;

import com.schoolsphere.lessons.api.ConferencingProvider;
import com.schoolsphere.lessons.internal.LessonsService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Live lesson rooms for a class (spec 7.5). */
@RestController
@RequestMapping("/api/v1/lessons")
public class LessonsController {

    private final LessonsService lessonsService;

    public LessonsController(LessonsService lessonsService) {
        this.lessonsService = lessonsService;
    }

    @GetMapping("/rooms")
    @PreAuthorize("@policy.can('school.view')")
    public ConferencingProvider.Room room(@RequestParam UUID classGroupId,
                                          @RequestParam(required = false) UUID subjectId,
                                          @RequestParam(required = false) String topic,
                                          @RequestParam(required = false) String date) {
        LocalDate parsed = date == null || date.isBlank() ? null : LocalDate.parse(date);
        return lessonsService.roomFor(classGroupId, subjectId, topic, parsed);
    }
}
