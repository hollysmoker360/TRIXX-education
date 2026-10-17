package com.schoolsphere.tenants.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The permission catalog (spec 8.2) and the default role-to-permission matrix (spec 8.3).
 * The full catalog is generated from this code into docs/PERMISSIONS.md in CI.
 */
public final class PermissionCatalog {

    private PermissionCatalog() {
    }

    public static final String SCHOOL_VIEW = "school.view";
    public static final String SCHOOL_SETTINGS_MANAGE = "school.settings.manage";
    public static final String PEOPLE_VIEW = "people.view";
    public static final String PEOPLE_MANAGE = "people.manage";
    public static final String PEOPLE_MANAGE_LIMITED = "people.manage.limited";
    public static final String ACADEMICS_VIEW = "academics.view";
    public static final String ACADEMICS_MANAGE = "academics.manage";
    public static final String TIMETABLE_VIEW = "timetable.view";
    public static final String TIMETABLE_EDIT = "timetable.edit";
    public static final String TIMETABLE_PUBLISH = "timetable.publish";
    public static final String ASSIGNMENT_CREATE = "assignment.create";
    public static final String ASSIGNMENT_SUBMIT = "assignment.submit";
    public static final String ASSIGNMENT_GRADE = "assignment.grade";
    public static final String GRADE_ENTER = "grade.enter";
    public static final String GRADE_PUBLISH = "grade.publish";
    public static final String GRADE_VIEW_OWN = "grade.view.own";
    public static final String CHILD_DATA_VIEW = "child.data.view";
    public static final String ATTENDANCE_TAKE = "attendance.take";
    public static final String ATTENDANCE_CORRECT = "attendance.correct";
    public static final String ATTENDANCE_REPORT = "attendance.report";
    public static final String LEARNING_MANAGE = "learning.manage";
    public static final String LEARNING_VIEW = "learning.view";
    public static final String CHAT_USE = "chat.use";
    public static final String CHAT_MODERATE = "chat.moderate";
    public static final String TASKS_OWN = "tasks.own";
    public static final String SUBSCRIPTION_MANAGE = "subscription.manage";
    public static final String AUDIT_VIEW = "audit.view";
    public static final String SYNC_DEVICE_MANAGE = "sync.device.manage";
    public static final String NOTIFICATION_SEND = "notification.send";

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_PRINCIPAL = "principal";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_DEPT_HEAD = "dept_head";
    public static final String ROLE_TEACHER = "teacher";
    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_PARENT = "parent";
    public static final String ROLE_BURSAR = "bursar";
    public static final String ROLE_LIBRARIAN = "librarian";
    public static final String ROLE_IT = "it_admin";

    private static final Set<String> EVERYONE = Set.of(SCHOOL_VIEW, CHAT_USE, TASKS_OWN);

    /** System roles and their permission bundles, seeded per school. */
    public static final Map<String, Set<String>> SYSTEM_ROLES = Map.ofEntries(
            Map.entry(ROLE_OWNER, Set.of(
                    SCHOOL_VIEW, SCHOOL_SETTINGS_MANAGE, PEOPLE_VIEW, PEOPLE_MANAGE, ACADEMICS_VIEW,
                    ACADEMICS_MANAGE, TIMETABLE_VIEW, TIMETABLE_EDIT, TIMETABLE_PUBLISH,
                    ATTENDANCE_REPORT, LEARNING_VIEW, CHAT_USE, CHAT_MODERATE, TASKS_OWN,
                    SUBSCRIPTION_MANAGE, AUDIT_VIEW, NOTIFICATION_SEND, SYNC_DEVICE_MANAGE)),
            Map.entry(ROLE_PRINCIPAL, Set.of(
                    SCHOOL_VIEW, SCHOOL_SETTINGS_MANAGE, PEOPLE_VIEW, PEOPLE_MANAGE, ACADEMICS_VIEW,
                    ACADEMICS_MANAGE, TIMETABLE_VIEW, TIMETABLE_EDIT, TIMETABLE_PUBLISH,
                    GRADE_ENTER, GRADE_PUBLISH, ATTENDANCE_TAKE, ATTENDANCE_CORRECT, ATTENDANCE_REPORT,
                    LEARNING_VIEW, LEARNING_MANAGE, CHAT_USE, CHAT_MODERATE, TASKS_OWN,
                    AUDIT_VIEW, NOTIFICATION_SEND, ASSIGNMENT_CREATE, ASSIGNMENT_GRADE)),
            Map.entry(ROLE_ADMIN, Set.of(
                    SCHOOL_VIEW, SCHOOL_SETTINGS_MANAGE, PEOPLE_VIEW, PEOPLE_MANAGE, ACADEMICS_VIEW,
                    ACADEMICS_MANAGE, TIMETABLE_VIEW, TIMETABLE_EDIT, TIMETABLE_PUBLISH,
                    GRADE_ENTER, GRADE_PUBLISH, ATTENDANCE_TAKE, ATTENDANCE_CORRECT, ATTENDANCE_REPORT,
                    LEARNING_VIEW, LEARNING_MANAGE, CHAT_USE, CHAT_MODERATE, TASKS_OWN,
                    AUDIT_VIEW, NOTIFICATION_SEND, ASSIGNMENT_GRADE)),
            Map.entry(ROLE_DEPT_HEAD, Set.of(
                    SCHOOL_VIEW, PEOPLE_VIEW, PEOPLE_MANAGE_LIMITED, ACADEMICS_VIEW,
                    TIMETABLE_VIEW, ASSIGNMENT_CREATE, ASSIGNMENT_GRADE, GRADE_ENTER, GRADE_PUBLISH,
                    ATTENDANCE_TAKE, ATTENDANCE_CORRECT, ATTENDANCE_REPORT,
                    LEARNING_VIEW, LEARNING_MANAGE, CHAT_USE, TASKS_OWN, NOTIFICATION_SEND)),
            Map.entry(ROLE_TEACHER, Set.of(
                    SCHOOL_VIEW, PEOPLE_VIEW, ACADEMICS_VIEW, TIMETABLE_VIEW,
                    ASSIGNMENT_CREATE, ASSIGNMENT_GRADE, GRADE_ENTER, ATTENDANCE_TAKE,
                    LEARNING_VIEW, LEARNING_MANAGE, CHAT_USE, TASKS_OWN, NOTIFICATION_SEND)),
            Map.entry(ROLE_STUDENT, Set.of(
                    SCHOOL_VIEW, TIMETABLE_VIEW, ASSIGNMENT_SUBMIT, GRADE_VIEW_OWN,
                    LEARNING_VIEW, CHAT_USE, TASKS_OWN)),
            Map.entry(ROLE_PARENT, Set.of(
                    SCHOOL_VIEW, CHILD_DATA_VIEW, TIMETABLE_VIEW, LEARNING_VIEW, CHAT_USE, TASKS_OWN)),
            Map.entry(ROLE_BURSAR, Set.of(
                    SCHOOL_VIEW, SUBSCRIPTION_MANAGE, PEOPLE_VIEW, ATTENDANCE_REPORT, CHAT_USE, TASKS_OWN)),
            Map.entry(ROLE_LIBRARIAN, Set.of(
                    SCHOOL_VIEW, LEARNING_VIEW, LEARNING_MANAGE, CHAT_USE, TASKS_OWN)),
            Map.entry(ROLE_IT, Set.of(
                    SCHOOL_VIEW, SCHOOL_SETTINGS_MANAGE, PEOPLE_VIEW, AUDIT_VIEW,
                    CHAT_USE, TASKS_OWN, SYNC_DEVICE_MANAGE))
    );

    public static final List<String> ALL_PERMISSIONS = List.of(
            SCHOOL_VIEW, SCHOOL_SETTINGS_MANAGE, PEOPLE_VIEW, PEOPLE_MANAGE, PEOPLE_MANAGE_LIMITED,
            ACADEMICS_VIEW, ACADEMICS_MANAGE, TIMETABLE_VIEW, TIMETABLE_EDIT, TIMETABLE_PUBLISH,
            ASSIGNMENT_CREATE, ASSIGNMENT_SUBMIT, ASSIGNMENT_GRADE, GRADE_ENTER, GRADE_PUBLISH,
            GRADE_VIEW_OWN, CHILD_DATA_VIEW, ATTENDANCE_TAKE, ATTENDANCE_CORRECT, ATTENDANCE_REPORT,
            LEARNING_MANAGE, LEARNING_VIEW, CHAT_USE, CHAT_MODERATE, TASKS_OWN,
            SUBSCRIPTION_MANAGE, AUDIT_VIEW, SYNC_DEVICE_MANAGE, NOTIFICATION_SEND);

    public static Set<String> defaultPermissionsFor(String roleCode) {
        Set<String> base = SYSTEM_ROLES.get(roleCode);
        if (base == null) {
            return EVERYONE;
        }
        Set<String> merged = new java.util.HashSet<>(EVERYONE);
        merged.addAll(base);
        return merged;
    }
}
