package com.schoolsphere.timetable.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Constraint checks for a timetable version (spec 12.1). Hard issues block publishing; soft
 * issues are workload guidance. All checks are pure over the entry list plus loaded lookups.
 */
@Component
public class TimetableValidator {

    public record Issue(String kind, String severity, String message, UUID entryId, UUID otherEntryId) {
    }

    public record Report(List<Issue> issues, int hardCount, int softCount, double score, Instant generatedAt) {
    }

    private static final int MAX_DAILY_PERIODS = 8;

    /**
     * @param validPeriodSeqs    period numbers that exist and are not breaks; empty list skips the check
     * @param teacherWeeklyLimit membership id to maximum weekly periods (null limits skipped)
     */
    public Report validate(List<TimetableEntry> entries, Set<Integer> validPeriodSeqs,
                           Map<UUID, Integer> teacherWeeklyLimit) {
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            TimetableEntry a = entries.get(i);
            if (!validPeriodSeqs.isEmpty() && !validPeriodSeqs.contains(a.getPeriodSeq())) {
                issues.add(new Issue("period_out_of_range", "hard",
                        "Period " + a.getPeriodSeq() + " is not a teaching period.", a.getId(), null));
            }
            if (a.getPeriodSeq() + a.getSpan() - 1 > 24) {
                issues.add(new Issue("period_out_of_range", "hard",
                        "The entry extends beyond the end of the day.", a.getId(), null));
            }
            for (int j = i + 1; j < entries.size(); j++) {
                TimetableEntry b = entries.get(j);
                if (!overlaps(a, b)) {
                    continue;
                }
                if (sameClass(a, b)) {
                    issues.add(new Issue("class_clash", "hard",
                            "The class has two lessons in the same slot.", a.getId(), b.getId()));
                }
                if (a.getTeacherMembershipId() != null
                        && a.getTeacherMembershipId().equals(b.getTeacherMembershipId())) {
                    issues.add(new Issue("teacher_clash", "hard",
                            "The teacher is double-booked in the same slot.", a.getId(), b.getId()));
                }
                if (a.getClassroomId() != null && a.getClassroomId().equals(b.getClassroomId())) {
                    issues.add(new Issue("room_clash", "hard",
                            "The room is double-booked in the same slot.", a.getId(), b.getId()));
                }
            }
        }
        issues.addAll(workloadIssues(entries, teacherWeeklyLimit));
        int hard = (int) issues.stream().filter(i -> "hard".equals(i.severity())).count();
        int soft = issues.size() - hard;
        double score = Math.max(0, 100 - 15 * hard - 2 * soft);
        return new Report(issues, hard, soft, score, Instant.now());
    }

    private List<Issue> workloadIssues(List<TimetableEntry> entries, Map<UUID, Integer> teacherWeeklyLimit) {
        List<Issue> issues = new ArrayList<>();
        Map<UUID, Integer> weekly = new HashMap<>();
        Map<UUID, Map<Integer, Integer>> daily = new HashMap<>();
        for (TimetableEntry entry : entries) {
            UUID teacher = entry.getTeacherMembershipId();
            if (teacher == null) {
                continue;
            }
            weekly.merge(teacher, entry.getSpan(), Integer::sum);
            daily.computeIfAbsent(teacher, k -> new HashMap<>())
                    .merge(entry.getDayOfWeek(), entry.getSpan(), Integer::sum);
        }
        for (Map.Entry<UUID, Map<Integer, Integer>> byTeacher : daily.entrySet()) {
            for (Map.Entry<Integer, Integer> day : byTeacher.getValue().entrySet()) {
                if (day.getValue() > MAX_DAILY_PERIODS) {
                    issues.add(new Issue("teacher_daily_load", "soft",
                            "A teacher has " + day.getValue() + " periods on day " + day.getKey() + ".",
                            null, null));
                }
            }
        }
        if (teacherWeeklyLimit != null) {
            for (Map.Entry<UUID, Integer> weeklyLoad : weekly.entrySet()) {
                Integer limit = teacherWeeklyLimit.get(weeklyLoad.getKey());
                if (limit != null && weeklyLoad.getValue() > limit) {
                    issues.add(new Issue("teacher_weekly_load", "soft",
                            "A teacher has " + weeklyLoad.getValue() + " periods per week, above the limit of "
                                    + limit + ".", null, null));
                }
            }
        }
        return issues;
    }

    public static boolean overlaps(TimetableEntry a, TimetableEntry b) {
        return a.getDayOfWeek() == b.getDayOfWeek()
                && a.getPeriodSeq() < b.getPeriodSeq() + b.getSpan()
                && b.getPeriodSeq() < a.getPeriodSeq() + a.getSpan();
    }

    public static boolean sameClass(TimetableEntry a, TimetableEntry b) {
        return a.getClassGroupId().equals(b.getClassGroupId()) && Objects.equals(a.getStreamId(), b.getStreamId());
    }
}
