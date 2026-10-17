package com.schoolsphere.tasks.web;

import com.schoolsphere.tasks.internal.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("@policy.can('tasks.own')")
    public List<TaskService.TaskView> tasks(@RequestParam(required = false) Boolean done,
                                            @RequestParam(required = false) Instant dueBefore) {
        return taskService.listTasks(done, dueBefore);
    }

    @PostMapping
    @PreAuthorize("@policy.can('tasks.own')")
    public TaskService.TaskView createTask(@Valid @RequestBody TaskRequest request) {
        return taskService.createTask(request.title(), request.description(), request.dueAt(), request.priority(),
                request.category(), request.relatedSubjectId(), request.relatedAssignmentId(), request.taskKey(),
                request.reminderAt());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@policy.can('tasks.own')")
    public TaskService.TaskView updateTask(@PathVariable UUID id, @RequestBody TaskRequest request) {
        return taskService.updateTask(id, request.title(), request.description(), request.dueAt(), request.priority(),
                request.category(), request.reminderAt());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@policy.can('tasks.own')")
    public TaskService.TaskView completeTask(@PathVariable UUID id) {
        return taskService.completeTask(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@policy.can('tasks.own')")
    public void deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
    }

    public record TaskRequest(@NotBlank String title, String description, Instant dueAt, String priority,
                              String category, UUID relatedSubjectId, UUID relatedAssignmentId, String taskKey,
                              Instant reminderAt) {
    }
}
