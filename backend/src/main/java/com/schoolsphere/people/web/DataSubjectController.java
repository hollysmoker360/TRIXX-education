package com.schoolsphere.people.web;

import com.schoolsphere.people.internal.DataSubjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data-subject access and erasure requests as first-class admin features (spec 3.3.5, 13.3).
 * Exports and erasures are audited; erasure never deletes records held under legal retention.
 */
@RestController
@RequestMapping("/api/v1/data-subjects")
public class DataSubjectController {

    private final DataSubjectService dataSubjectService;

    public DataSubjectController(DataSubjectService dataSubjectService) {
        this.dataSubjectService = dataSubjectService;
    }

    @PostMapping("/export")
    @PreAuthorize("@policy.can('people.manage')")
    public DataSubjectService.DataSubjectExport export(@Valid @RequestBody ExportRequest request) {
        return dataSubjectService.export(request.subjectUserId(), request.studentProfileId());
    }

    @PostMapping("/erase")
    @PreAuthorize("@policy.can('people.manage')")
    public DataSubjectService.EraseResult erase(@Valid @RequestBody EraseRequest request) {
        return dataSubjectService.erase(request.subjectUserId(), request.mode(), request.reason());
    }

    public record ExportRequest(@NotNull UUID subjectUserId, UUID studentProfileId) {
    }

    public record EraseRequest(@NotNull UUID subjectUserId, String mode, String reason) {
    }
}
