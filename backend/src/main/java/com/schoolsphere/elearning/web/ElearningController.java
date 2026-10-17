package com.schoolsphere.elearning.web;

import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.elearning.internal.ElearningService;
import com.schoolsphere.elearning.internal.ElearningService.CourseInput;
import com.schoolsphere.elearning.internal.ElearningService.CourseView;
import com.schoolsphere.elearning.internal.ElearningService.EnrollResult;
import com.schoolsphere.elearning.internal.ElearningService.ItemView;
import com.schoolsphere.elearning.internal.ElearningService.LearnerProgressView;
import com.schoolsphere.elearning.internal.ElearningService.MyCourseView;
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
@RequestMapping("/api/v1/elearning")
public class ElearningController {

    private final ElearningService elearningService;

    public ElearningController(ElearningService elearningService) {
        this.elearningService = elearningService;
    }

    // ----- staff -----

    @GetMapping("/courses")
    @PreAuthorize("@policy.can('learning.manage')")
    public PageResponse<CourseView> courses(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return elearningService.listCourses(status, page, size);
    }

    @PostMapping("/courses")
    @PreAuthorize("@policy.can('learning.manage')")
    public CourseView createCourse(@RequestBody CourseInput request) {
        return elearningService.createCourse(request);
    }

    @PatchMapping("/courses/{id}")
    @PreAuthorize("@policy.can('learning.manage')")
    public CourseView updateCourse(@PathVariable UUID id, @RequestBody CourseInput request) {
        return elearningService.updateCourse(id, request);
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("@policy.can('learning.manage')")
    public void deleteCourse(@PathVariable UUID id) {
        elearningService.deleteCourse(id);
    }

    @PostMapping("/courses/{id}/publish")
    @PreAuthorize("@policy.can('learning.manage')")
    public CourseView publish(@PathVariable UUID id) {
        return elearningService.publishCourse(id);
    }

    @PostMapping("/courses/{id}/archive")
    @PreAuthorize("@policy.can('learning.manage')")
    public CourseView archive(@PathVariable UUID id) {
        return elearningService.archiveCourse(id);
    }

    @GetMapping("/courses/{id}/items")
    @PreAuthorize("@policy.can('learning.view')")
    public List<ItemView> items(@PathVariable UUID id) {
        return elearningService.listItems(id);
    }

    @PostMapping("/courses/{id}/items")
    @PreAuthorize("@policy.can('learning.manage')")
    public ItemView addItem(@PathVariable UUID id, @RequestBody ItemRequest request) {
        return elearningService.addItem(id, request.itemType(), request.refId(), request.required());
    }

    @DeleteMapping("/courses/{id}/items/{itemId}")
    @PreAuthorize("@policy.can('learning.manage')")
    public void removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        elearningService.removeItem(id, itemId);
    }

    @PostMapping("/courses/{id}/enroll")
    @PreAuthorize("@policy.can('learning.manage')")
    public EnrollResult enroll(@PathVariable UUID id, @RequestBody(required = false) EnrollRequest request) {
        return elearningService.enroll(id, request == null ? null : request.studentIds());
    }

    @GetMapping("/courses/{id}/progress")
    @PreAuthorize("@policy.can('learning.manage')")
    public List<LearnerProgressView> progress(@PathVariable UUID id) {
        return elearningService.courseProgress(id);
    }

    // ----- learner -----

    @GetMapping("/courses/for-me")
    @PreAuthorize("@policy.can('learning.view')")
    public List<CourseView> coursesForMe() {
        return elearningService.listCoursesForMe();
    }

    @PostMapping("/courses/{id}/join")
    @PreAuthorize("@policy.can('learning.view')")
    public CourseView join(@PathVariable UUID id) {
        return elearningService.join(id);
    }

    @GetMapping("/courses/{id}/my-progress")
    @PreAuthorize("@policy.can('learning.view')")
    public MyCourseView myProgress(@PathVariable UUID id) {
        return elearningService.myProgress(id);
    }

    public record ItemRequest(String itemType, UUID refId, Boolean required) {
    }

    public record EnrollRequest(List<UUID> studentIds) {
    }
}
