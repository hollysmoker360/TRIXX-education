package com.schoolsphere.tasks.internal;

import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Personal tasks, owned by the creating user; all record-level checks live here (spec 11.3). */
@Service
@TenantScoped
public class TaskService {

    private static final List<String> PRIORITIES = List.of("low", "medium", "high");

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskView> listTasks(Boolean done, Instant dueBefore) {
        UUID userId = SecurityUtils.requireUserId();
        return taskRepository.findBySchoolIdAndOwnerUserIdAndDeletedAtIsNullOrderByDueAtAsc(schoolId(), userId).stream()
                .filter(task -> done == null || done == (task.getDoneAt() != null))
                .filter(task -> dueBefore == null || (task.getDueAt() != null && task.getDueAt().isBefore(dueBefore)))
                .map(this::toView).toList();
    }

    public long countOpenTasks() {
        return taskRepository.countBySchoolIdAndOwnerUserIdAndDoneAtIsNullAndDeletedAtIsNull(schoolId(),
                SecurityUtils.requireUserId());
    }

    public TaskView createTask(String title, String description, Instant dueAt, String priority, String category,
                               UUID relatedSubjectId, UUID relatedAssignmentId, String taskKey, Instant reminderAt) {
        UUID userId = SecurityUtils.requireUserId();
        if (taskKey != null && !taskKey.isBlank()) {
            Optional<Task> existing = taskRepository
                    .findBySchoolIdAndOwnerUserIdAndTaskKeyAndDeletedAtIsNull(schoolId(), userId, taskKey);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }
        Task task = new Task();
        task.setSchoolId(schoolId());
        task.setOwnerUserId(userId);
        task.setOwnerMembershipId(TenantContext.actorMembershipId());
        task.setTitle(title);
        task.setDescription(description);
        task.setDueAt(dueAt);
        task.setPriority(validatePriority(priority == null ? "medium" : priority));
        task.setCategory(category);
        task.setRelatedSubjectId(relatedSubjectId);
        task.setRelatedAssignmentId(relatedAssignmentId);
        task.setTaskKey(taskKey);
        task.setReminderAt(reminderAt);
        task.setCreatedBy(TenantContext.actorUserId());
        task.setUpdatedBy(TenantContext.actorUserId());
        return toView(taskRepository.save(task));
    }

    /** Creates a task for another user (generated suggestions such as assignment reminders). */
    public TaskView createForOwner(UUID ownerUserId, String title, Instant dueAt, String priority, String category,
                                   UUID relatedAssignmentId, String taskKey) {
        if (taskKey != null && !taskKey.isBlank()) {
            Optional<Task> existing = taskRepository
                    .findBySchoolIdAndOwnerUserIdAndTaskKeyAndDeletedAtIsNull(schoolId(), ownerUserId, taskKey);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }
        Task task = new Task();
        task.setSchoolId(schoolId());
        task.setOwnerUserId(ownerUserId);
        task.setTitle(title);
        task.setDueAt(dueAt);
        task.setPriority(validatePriority(priority == null ? "medium" : priority));
        task.setCategory(category);
        task.setRelatedAssignmentId(relatedAssignmentId);
        task.setTaskKey(taskKey);
        return toView(taskRepository.save(task));
    }

    public TaskView updateTask(UUID id, String title, String description, Instant dueAt, String priority,
                               String category, Instant reminderAt) {
        Task task = requireOwnTask(id);
        if (title != null) {
            task.setTitle(title);
        }
        if (description != null) {
            task.setDescription(description);
        }
        if (dueAt != null) {
            task.setDueAt(dueAt);
        }
        if (priority != null) {
            task.setPriority(validatePriority(priority));
        }
        if (category != null) {
            task.setCategory(category);
        }
        if (reminderAt != null) {
            task.setReminderAt(reminderAt);
        }
        task.setUpdatedBy(TenantContext.actorUserId());
        return toView(taskRepository.save(task));
    }

    public TaskView completeTask(UUID id) {
        Task task = requireOwnTask(id);
        if (task.getDoneAt() == null) {
            task.setDoneAt(Instant.now());
            task.setUpdatedBy(TenantContext.actorUserId());
            task = taskRepository.save(task);
        }
        return toView(task);
    }

    public void deleteTask(UUID id) {
        Task task = requireOwnTask(id);
        task.setDeletedAt(Instant.now());
        task.setUpdatedBy(TenantContext.actorUserId());
        taskRepository.save(task);
    }

    private Task requireOwnTask(UUID id) {
        Task task = taskRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("task_not_found", "Task not found."));
        if (!task.getOwnerUserId().equals(SecurityUtils.requireUserId())) {
            throw ApiException.forbidden("not_task_owner", "Only the owner can change this task.");
        }
        return task;
    }

    private String validatePriority(String priority) {
        if (!PRIORITIES.contains(priority)) {
            throw ApiException.badRequest("invalid_priority", "Priority must be low, medium, or high.");
        }
        return priority;
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private TaskView toView(Task task) {
        return new TaskView(task.getId(), task.getOwnerUserId(), task.getTitle(), task.getDescription(),
                task.getDueAt(), task.getPriority(), task.getCategory(), task.getRelatedSubjectId(),
                task.getRelatedAssignmentId(), task.getTaskKey(), task.getReminderAt(), task.getDoneAt(),
                task.getCreatedAt());
    }

    public record TaskView(UUID id, UUID ownerUserId, String title, String description, Instant dueAt,
                           String priority, String category, UUID relatedSubjectId, UUID relatedAssignmentId,
                           String taskKey, Instant reminderAt, Instant doneAt, Instant createdAt) {
    }
}
