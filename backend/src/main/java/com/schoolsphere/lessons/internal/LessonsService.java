package com.schoolsphere.lessons.internal;

import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.lessons.api.ConferencingProvider;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.PermissionCatalog;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Live lesson rooms (spec 7.5). Staff may open a room for any class; students and parents may open
 * only rooms for classes they belong to (enrollment / guardianship checked through PeopleApi).
 */
@Service
@TenantScoped
public class LessonsService {

    private static final Set<String> STAFF_PERMISSIONS = Set.of(
            PermissionCatalog.ASSIGNMENT_CREATE, PermissionCatalog.ATTENDANCE_TAKE,
            PermissionCatalog.LEARNING_MANAGE);

    private final ConferencingProvider conferencingProvider;
    private final PeopleApi peopleApi;
    private final ZoneId zoneId;

    public LessonsService(ConferencingProvider conferencingProvider, PeopleApi peopleApi,
                          @Value("${schoolsphere.timezone:Africa/Nairobi}") String timezone) {
        this.conferencingProvider = conferencingProvider;
        this.peopleApi = peopleApi;
        this.zoneId = ZoneId.of(timezone);
    }

    public ConferencingProvider.Room roomFor(UUID classGroupId, UUID subjectId, String topic, LocalDate date) {
        if (classGroupId == null) {
            throw ApiException.badRequest("missing_class", "A class group is required.");
        }
        UUID schoolId = TenantContext.schoolId();
        UUID userId = SecurityUtils.requireUserId();
        if (!isStaff() && !belongsToClass(schoolId, userId, classGroupId)) {
            throw ApiException.forbidden("not_in_class", "You do not have access to this class's lessons.");
        }
        LocalDate effectiveDate = date == null ? LocalDate.now(zoneId) : date;
        return conferencingProvider.room(schoolId, classGroupId, subjectId, topic, effectiveDate);
    }

    private boolean isStaff() {
        var principal = SecurityUtils.principalOrNull();
        return principal != null && principal.permissions().stream().anyMatch(STAFF_PERMISSIONS::contains);
    }

    private boolean belongsToClass(UUID schoolId, UUID userId, UUID classGroupId) {
        Set<UUID> classStudents = peopleApi.listStudentsByClass(schoolId, classGroupId, null).stream()
                .map(PeopleApi.StudentRef::id)
                .collect(Collectors.toSet());
        if (classStudents.isEmpty()) {
            return false;
        }
        var me = peopleApi.studentForUser(schoolId, userId);
        if (me.isPresent() && classStudents.contains(me.get().id())) {
            return true;
        }
        return peopleApi.childrenOfParent(schoolId, userId).stream()
                .anyMatch(child -> classStudents.contains(child.student().id()));
    }
}
