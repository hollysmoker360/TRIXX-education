package com.schoolsphere.timetable.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.schoolsphere.timetable.internal.LessonReminderPlanner.Occurrence;
import com.schoolsphere.timetable.internal.LessonReminderPlanner.Reminder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reminder planning (spec 12.2): who is reminded, when, and the key that makes re-running
 * materialisation idempotent.
 */
class LessonReminderPlannerTest {

    private static final UUID OCCURRENCE = UUID.randomUUID();
    private static final UUID TEACHER = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-10-19T05:00:00Z");

    private static Occurrence occurrence(int span) {
        return new Occurrence(OCCURRENCE, "Form 2 East", "Mathematics", START, span);
    }

    private static Map<UUID, Set<UUID>> recipients() {
        return Map.of(OCCURRENCE, Set.of(TEACHER, STUDENT));
    }

    @Test
    void remindsEveryRecipientTwoMinutesBeforeByDefault() {
        List<Reminder> reminders =
                LessonReminderPlanner.plan(List.of(occurrence(1)), recipients(), Map.of(), 2);

        assertThat(reminders).hasSize(2);
        assertThat(reminders).allSatisfy(reminder -> {
            assertThat(reminder.offsetMinutes()).isEqualTo(2);
            assertThat(reminder.dueAt()).isEqualTo(START.minusSeconds(120));
            assertThat(reminder.dedupeKey()).isEqualTo("lesson.reminder:" + OCCURRENCE + ":2");
        });
        assertThat(reminders).extracting(Reminder::userId).containsExactlyInAnyOrder(TEACHER, STUDENT);
    }

    @Test
    void aDoubleLessonSendsOneReminderAtTheStart() {
        List<Reminder> reminders =
                LessonReminderPlanner.plan(List.of(occurrence(2)), Map.of(OCCURRENCE, Set.of(TEACHER)),
                        Map.of(), 2);

        // One occurrence, therefore one reminder, and the body says it is a double period.
        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).dueAt()).isEqualTo(START.minusSeconds(120));
        assertThat(reminders.get(0).body()).contains("double period");
    }

    @Test
    void aPerUserOverrideChangesTheLeadTimeAndTheKey() {
        List<Reminder> reminders = LessonReminderPlanner.plan(List.of(occurrence(1)), recipients(),
                Map.of(TEACHER, 5), 2);

        Reminder teacher = reminders.stream().filter(r -> r.userId().equals(TEACHER)).findFirst().orElseThrow();
        Reminder student = reminders.stream().filter(r -> r.userId().equals(STUDENT)).findFirst().orElseThrow();
        assertThat(teacher.offsetMinutes()).isEqualTo(5);
        assertThat(teacher.dueAt()).isEqualTo(START.minusSeconds(300));
        assertThat(teacher.dedupeKey()).isEqualTo("lesson.reminder:" + OCCURRENCE + ":5");
        assertThat(student.dedupeKey()).isEqualTo("lesson.reminder:" + OCCURRENCE + ":2");
    }

    @Test
    void planningIsStableSoAReminderIsNeverScheduledTwice() {
        List<Reminder> first =
                LessonReminderPlanner.plan(List.of(occurrence(1)), recipients(), Map.of(), 2);
        List<Reminder> second =
                LessonReminderPlanner.plan(List.of(occurrence(1)), recipients(), Map.of(), 2);

        assertThat(first).extracting(Reminder::dedupeKey)
                .containsExactlyInAnyOrderElementsOf(second.stream().map(Reminder::dedupeKey).toList());
    }

    @Test
    void aZeroLeadTimeFiresAtTheStartAndNoOccurrenceMeansNoReminder() {
        assertThat(LessonReminderPlanner.plan(List.of(occurrence(1)), recipients(), Map.of(), 0).get(0)
                .dueAt()).isEqualTo(START);
        assertThat(LessonReminderPlanner.plan(List.of(), Map.of(), Map.of(), 2)).isEmpty();
    }
}
