package com.schoolsphere.tasks.internal;

import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.learning.api.AssignmentPublishedEvent;
import com.schoolsphere.people.api.PeopleApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates one personal task per student when an assignment is published. Runs with an explicit
 * tenant context so the dedupe lookup and insert are visible under row-level security.
 */
@Component
public class AssignmentPublishedListener {

    private final PeopleApi peopleApi;
    private final TaskService taskService;

    public AssignmentPublishedListener(PeopleApi peopleApi, TaskService taskService) {
        this.peopleApi = peopleApi;
        this.taskService = taskService;
    }

    @EventListener
    public void onAssignmentPublished(AssignmentPublishedEvent event) {
        TenantContext.run(event.schoolId(), null, null, () -> {
            for (PeopleApi.StudentRef student : peopleApi.listStudentsByClass(event.schoolId(), event.classGroupId(),
                    event.streamId())) {
                if (student.userId() == null) {
                    continue;
                }
                taskService.createForOwner(student.userId(), "Assignment: " + event.title(), event.dueAt(),
                        "medium", "assignment", event.assignmentId(), "assignment:" + event.assignmentId());
            }
            return null;
        });
    }
}
