package com.schoolsphere.timetable.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Heuristic timetable solver (spec 12.1): seeded greedy placement with randomized restarts and a
 * single-level repair pass. Deterministic for a given seed; full CP-SAT (OR-Tools) is a documented
 * follow-up (docs/adr).
 */
@Component
public class TimetableGenerator {

    public record Demand(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                         int periodsPerWeek) {
    }

    public record Placement(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                            UUID classroomId, int day, int period, int span) {
    }

    public record Result(List<Placement> placements, long seed, int requested, int placed,
                         int roomsUnassigned, int attemptsUsed) {

        public int unplaced() {
            return requested - placed;
        }
    }

    public Result generate(List<Demand> demands, List<Placement> locked, List<Integer> days, List<Integer> periods,
                           List<UUID> classrooms, long seed, int attempts) {
        List<Demand> expanded = new ArrayList<>();
        for (Demand demand : demands) {
            for (int i = 0; i < demand.periodsPerWeek(); i++) {
                expanded.add(demand);
            }
        }
        List<Integer> sortedDays = new ArrayList<>(days);
        Collections.sort(sortedDays);
        List<Integer> sortedPeriods = new ArrayList<>(periods);
        Collections.sort(sortedPeriods);

        int attemptsUsed = 0;
        Solution best = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Random random = new Random(seed + attempt);
            Solution solution = tryBuild(expanded, locked, sortedDays, sortedPeriods, classrooms, random);
            attemptsUsed++;
            if (best == null || solution.betterThan(best)) {
                best = solution;
            }
            if (best.placed == expanded.size() && best.roomsUnassigned == 0) {
                break;
            }
        }
        if (best == null) {
            best = new Solution(new ArrayList<>(), 0);
        }
        return new Result(best.placements.stream().map(Token::toPlacement).toList(), seed,
                expanded.size(), best.placed, best.roomsUnassigned, attemptsUsed);
    }

    private Solution tryBuild(List<Demand> lessons, List<Placement> locked, List<Integer> days,
                              List<Integer> periods, List<UUID> classrooms, Random random) {
        Grid grid = new Grid();
        List<Token> tokens = new ArrayList<>();
        for (Placement placement : locked) {
            Token token = Token.from(placement);
            grid.place(token);
            tokens.add(token);
        }
        List<Demand> order = new ArrayList<>(lessons);
        Map<UUID, Integer> teacherLoad = new HashMap<>();
        for (Demand demand : order) {
            if (demand.teacherMembershipId() != null) {
                teacherLoad.merge(demand.teacherMembershipId(), demand.periodsPerWeek(), Integer::sum);
            }
        }
        order.sort((a, b) -> {
            int byTeacher = Integer.compare(
                    teacherLoad.getOrDefault(b.teacherMembershipId(), 0),
                    teacherLoad.getOrDefault(a.teacherMembershipId(), 0));
            return byTeacher != 0 ? byTeacher : random.nextInt(3) - 1;
        });

        List<Token> unplaced = new ArrayList<>();
        for (Demand demand : order) {
            Token token = Token.of(demand);
            if (!assignBestSlot(grid, token, days, periods, classrooms, random)) {
                unplaced.add(token);
            }
        }
        tokens.addAll(unplaced);

        repair(grid, unplaced, days, periods, classrooms, random);

        List<Token> finalTokens = new ArrayList<>();
        int roomsFinal = 0;
        for (Token token : tokens) {
            if (token.day != 0) {
                if (token.room == null && !classrooms.isEmpty()) {
                    roomsFinal++;
                }
                finalTokens.add(token);
            }
        }
        return new Solution(finalTokens, finalTokens.size(), roomsFinal);
    }

    private boolean assignBestSlot(Grid grid, Token token, List<Integer> days, List<Integer> periods,
                                   List<UUID> classrooms, Random random) {
        List<Integer> dayOrder = shuffled(days, random);
        List<Integer> periodOrder = shuffled(periods, random);
        List<UUID> roomOrder = shuffled(classrooms, random);
        for (int day : dayOrder) {
            for (int period : periodOrder) {
                if (!grid.free(token, day, period, null)) {
                    continue;
                }
                UUID room = null;
                for (UUID candidate : roomOrder) {
                    if (grid.free(token, day, period, candidate)) {
                        room = candidate;
                        break;
                    }
                }
                token.day = day;
                token.period = period;
                token.room = room;
                grid.place(token);
                return true;
            }
        }
        return false;
    }

    /** Single-level repair: displace one blocker per unplaced lesson and retry. */
    private void repair(Grid grid, List<Token> unplaced, List<Integer> days, List<Integer> periods,
                        List<UUID> classrooms, Random random) {
        for (Token token : new ArrayList<>(unplaced)) {
            boolean fixed = false;
            List<Integer> dayOrder = shuffled(days, random);
            List<Integer> periodOrder = shuffled(periods, random);
            for (int day : dayOrder) {
                for (int period : periodOrder) {
                    List<Token> blockers = grid.blockers(token, day, period);
                    if (blockers.size() != 1) {
                        continue;
                    }
                    Token blocker = blockers.get(0);
                    Token savedDay = new Token(blocker);
                    grid.unplace(blocker);
                    if (assignBestSlot(grid, blocker, days, periods, classrooms, random)) {
                        if (grid.free(token, day, period, null)) {
                            UUID room = null;
                            for (UUID candidate : shuffled(classrooms, random)) {
                                if (grid.free(token, day, period, candidate)) {
                                    room = candidate;
                                    break;
                                }
                            }
                            token.day = day;
                            token.period = period;
                            token.room = room;
                            grid.place(token);
                            unplaced.remove(token);
                            fixed = true;
                            break;
                        }
                        grid.unplace(blocker);
                    }
                    blocker.copyFrom(savedDay);
                    grid.place(blocker);
                }
                if (fixed) {
                    break;
                }
            }
        }
    }

    private static <T> List<T> shuffled(List<T> source, Random random) {
        List<T> copy = new ArrayList<>(source);
        Collections.shuffle(copy, random);
        return copy;
    }

    private static int slotKey(int day, int period) {
        return day * 100 + period;
    }

    private static final class Token {
        UUID classGroupId;
        UUID streamId;
        UUID subjectId;
        UUID teacherMembershipId;
        UUID room;
        int day;
        int period;
        int span;

        Token(Demand demand) {
            this.classGroupId = demand.classGroupId();
            this.streamId = demand.streamId();
            this.subjectId = demand.subjectId();
            this.teacherMembershipId = demand.teacherMembershipId();
            this.span = 1;
        }

        Token(Placement placement) {
            this.classGroupId = placement.classGroupId();
            this.streamId = placement.streamId();
            this.subjectId = placement.subjectId();
            this.teacherMembershipId = placement.teacherMembershipId();
            this.room = placement.classroomId();
            this.day = placement.day();
            this.period = placement.period();
            this.span = placement.span();
        }

        Token(Token other) {
            copyFrom(other);
        }

        static Token of(Demand demand) {
            return new Token(demand);
        }

        static Token from(Placement placement) {
            return new Token(placement);
        }

        void copyFrom(Token other) {
            this.classGroupId = other.classGroupId;
            this.streamId = other.streamId;
            this.subjectId = other.subjectId;
            this.teacherMembershipId = other.teacherMembershipId;
            this.room = other.room;
            this.day = other.day;
            this.period = other.period;
            this.span = other.span;
        }

        String classKey() {
            return classGroupId + ":" + streamId;
        }

        Placement toPlacement() {
            return new Placement(classGroupId, streamId, subjectId, teacherMembershipId, room, day, period, span);
        }
    }

    /** Occupancy grid for lessons; a lesson occupies its class, teacher and room across its span. */
    private static final class Grid {
        private final Map<String, Map<Integer, Token>> classSlots = new HashMap<>();
        private final Map<UUID, Map<Integer, Token>> teacherSlots = new HashMap<>();
        private final Map<UUID, Map<Integer, Token>> roomSlots = new HashMap<>();

        boolean free(Token token, int day, int period, UUID room) {
            if (!freeForClassOnly(token, day, period)) {
                return false;
            }
            for (int s = period; s < period + token.span; s++) {
                int key = slotKey(day, s);
                if (token.teacherMembershipId != null
                        && teacherSlots.getOrDefault(token.teacherMembershipId, Map.of()).containsKey(key)) {
                    return false;
                }
                if (room != null && roomSlots.getOrDefault(room, Map.of()).containsKey(key)) {
                    return false;
                }
            }
            return true;
        }

        boolean freeForClassOnly(Token token, int day, int period) {
            for (int s = period; s < period + token.span; s++) {
                if (classSlots.getOrDefault(token.classKey(), Map.of()).containsKey(slotKey(day, s))) {
                    return false;
                }
            }
            return true;
        }

        List<Token> blockers(Token token, int day, int period) {
            List<Token> blockers = new ArrayList<>();
            for (int s = period; s < period + token.span; s++) {
                int key = slotKey(day, s);
                Token byClass = classSlots.getOrDefault(token.classKey(), Map.of()).get(key);
                if (byClass != null && !blockers.contains(byClass)) {
                    blockers.add(byClass);
                }
                if (token.teacherMembershipId != null) {
                    Token byTeacher = teacherSlots.getOrDefault(token.teacherMembershipId, Map.of()).get(key);
                    if (byTeacher != null && !blockers.contains(byTeacher)) {
                        blockers.add(byTeacher);
                    }
                }
            }
            return blockers;
        }

        void place(Token token) {
            for (int s = token.period; s < token.period + token.span; s++) {
                int key = slotKey(token.day, s);
                classSlots.computeIfAbsent(token.classKey(), k -> new HashMap<>()).put(key, token);
                if (token.teacherMembershipId != null) {
                    teacherSlots.computeIfAbsent(token.teacherMembershipId, k -> new HashMap<>()).put(key, token);
                }
                if (token.room != null) {
                    roomSlots.computeIfAbsent(token.room, k -> new HashMap<>()).put(key, token);
                }
            }
        }

        void unplace(Token token) {
            for (int s = token.period; s < token.period + token.span; s++) {
                int key = slotKey(token.day, s);
                classSlots.getOrDefault(token.classKey(), Map.of()).remove(key);
                if (token.teacherMembershipId != null) {
                    teacherSlots.getOrDefault(token.teacherMembershipId, Map.of()).remove(key);
                }
                if (token.room != null) {
                    roomSlots.getOrDefault(token.room, Map.of()).remove(key);
                }
            }
            token.day = 0;
        }
    }

    private static final class Solution {
        final List<Token> placements;
        final int placed;
        final int roomsUnassigned;

        Solution(List<Token> placements, int placed) {
            this(placements, placed, 0);
        }

        Solution(List<Token> placements, int placed, int roomsUnassigned) {
            this.placements = placements;
            this.placed = placed;
            this.roomsUnassigned = roomsUnassigned;
        }

        boolean betterThan(Solution other) {
            if (placed != other.placed) {
                return placed > other.placed;
            }
            return roomsUnassigned < other.roomsUnassigned;
        }
    }
}
