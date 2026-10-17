package com.schoolsphere.learning.web;

import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.learning.internal.LearningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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

@RestController
@RequestMapping("/api/v1/learning")
public class ResourceController {

    private final LearningService learningService;

    public ResourceController(LearningService learningService) {
        this.learningService = learningService;
    }

    // ----- management -----

    @GetMapping("/resources")
    @PreAuthorize("@policy.can('learning.manage')")
    public PageResponse<LearningService.ResourceView> resources(@RequestParam(required = false) UUID classGroupId,
                                                                @RequestParam(required = false) UUID subjectId,
                                                                @RequestParam(required = false) String state,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return learningService.listResources(classGroupId, subjectId, state, page, size);
    }

    @PostMapping("/resources")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.ResourceView createResource(@Valid @RequestBody ResourceRequest request) {
        return learningService.createResource(request.title(), request.description(), request.resourceType(),
                request.topic(), request.subjectId(), request.classGroupId(), request.streamId(),
                request.academicYearId(), request.termId(), request.audience());
    }

    @GetMapping("/resources/{id}")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.ResourceView resource(@PathVariable UUID id) {
        return learningService.getResource(id);
    }

    @PatchMapping("/resources/{id}")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.ResourceView updateResource(@PathVariable UUID id,
                                                       @RequestBody ResourceRequest request) {
        return learningService.updateResource(id, request.title(), request.description(), request.resourceType(),
                request.topic(), request.subjectId(), request.classGroupId(), request.streamId(),
                request.academicYearId(), request.termId(), request.audience());
    }

    @DeleteMapping("/resources/{id}")
    @PreAuthorize("@policy.can('learning.manage')")
    public void deleteResource(@PathVariable UUID id) {
        learningService.deleteResource(id);
    }

    @PostMapping("/resources/{id}/publish")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.ResourceView publishResource(@PathVariable UUID id,
                                                        @RequestBody(required = false) PublishRequest request) {
        return learningService.publishResource(id, request == null ? null : request.publishAt());
    }

    @PostMapping("/resources/{id}/archive")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.ResourceView archiveResource(@PathVariable UUID id) {
        return learningService.archiveResource(id);
    }

    @GetMapping("/resources/{id}/versions")
    @PreAuthorize("@policy.can('learning.manage')")
    public List<LearningService.VersionView> versions(@PathVariable UUID id) {
        return learningService.listResourceVersions(id);
    }

    @PostMapping("/resources/{id}/versions")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.VersionView addVersion(@PathVariable UUID id,
                                                  @Valid @RequestBody VersionRequest request) {
        return learningService.addResourceVersion(id, request.storageKey(), request.mimeType(), request.sizeBytes(),
                request.checksum(), request.externalUrl(), request.textBody(), request.note());
    }

    @PostMapping("/resources/{id}/versions/{versionId}/restore")
    @PreAuthorize("@policy.can('learning.manage')")
    public LearningService.VersionView restoreVersion(@PathVariable UUID id, @PathVariable UUID versionId) {
        return learningService.restoreResourceVersion(id, versionId);
    }

    // ----- learner side -----

    @GetMapping("/resources/for-me")
    public List<LearningService.LearnerResourceView> resourcesForMe() {
        return learningService.listResourcesForMe();
    }

    @PostMapping("/resources/{id}/bookmark")
    public LearningService.BookmarkView toggleBookmark(@PathVariable UUID id) {
        return learningService.toggleBookmark(id);
    }

    @PostMapping("/resources/{id}/complete")
    public LearningService.CompletionView markComplete(@PathVariable UUID id) {
        return learningService.markComplete(id);
    }

    public record ResourceRequest(@NotBlank String title, String description, @NotBlank String resourceType,
                                  String topic, UUID subjectId, UUID classGroupId, UUID streamId, UUID academicYearId,
                                  UUID termId, Map<String, Object> audience) {
    }

    public record VersionRequest(String storageKey, String mimeType, Long sizeBytes, String checksum,
                                 String externalUrl, String textBody, String note) {
    }

    public record PublishRequest(Instant publishAt) {
    }
}
