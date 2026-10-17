package com.schoolsphere.timetable.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Second step of generation (spec 12.1): the solver places lessons in time, then rooms are assigned
 * greedily, without changing any placement. A room is reusable for any lesson whose (day, periods) do
 * not overlap with what that room already holds.
 */
public final class RoomAssigner {

    public record Lesson(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                         int day, int period, int span) {
    }

    public record Assigned(Lesson lesson, UUID classroomId) {
    }

    private RoomAssigner() {
    }

    /** Assigns rooms first-fit, in the order the lessons are given. */
    public static List<Assigned> assign(List<Lesson> lessons, List<UUID> classrooms) {
        List<Assigned> assigned = new ArrayList<>();
        Map<UUID, Set<Integer>> occupancy = new LinkedHashMap<>();
        for (UUID classroom : classrooms) {
            occupancy.put(classroom, new HashSet<>());
        }
        List<UUID> orderedRooms = new ArrayList<>(classrooms);
        for (Lesson lesson : lessons) {
            UUID room = null;
            for (UUID candidate : orderedRooms) {
                Set<Integer> used = occupancy.get(candidate);
                boolean free = true;
                for (int offset = 0; offset < Math.max(1, lesson.span()); offset++) {
                    int slot = lesson.day() * 1000 + (lesson.period() + offset);
                    if (used.contains(slot)) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    for (int offset = 0; offset < Math.max(1, lesson.span()); offset++) {
                        used.add(lesson.day() * 1000 + (lesson.period() + offset));
                    }
                    room = candidate;
                    break;
                }
            }
            assigned.add(new Assigned(lesson, room));
        }
        return assigned;
    }

    public static long unassigned(List<Assigned> assigned) {
        return assigned.stream().filter(a -> a.classroomId() == null).count();
    }
}
