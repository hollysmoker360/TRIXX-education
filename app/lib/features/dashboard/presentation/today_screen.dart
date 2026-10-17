import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/navigation/destinations.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// The teacher and student landing page — the most important screen to design
/// well (specification section 5.3). It says what to do next rather than showing
/// numbers alone.
///
/// The page opens with the signature Trixx [HeroHeader] so the greeting, the
/// date, and the two most common actions sit in one glanceable block, then the
/// KPI tiles, then the two worklists.
class TodayScreen extends ConsumerWidget {
  const TodayScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final roles = session.membership?.roles.toSet() ?? <String>{};
    final isTeacher = roles.intersection({'teacher', 'dept_head', 'principal', 'admin', 'owner'}).isNotEmpty;

    return ScreenScaffold(
      title: s.today,
      header: HeroHeader(
        eyebrow: s.today,
        icon: Icons.today_outlined,
        title: _greeting(context, session.user?.displayName ?? ''),
        subtitle: s.heroTodaySubtitle,
        actions: [
          HeroAction(
            icon: Icons.schedule_outlined,
            label: s.timetable,
            onPressed: () => goToShellDestination(ref, ShellDestination.timetable),
          ),
          if (isTeacher)
            HeroAction(
              icon: Icons.fact_check_outlined,
              label: s.attendance,
              onPressed: () => goToShellDestination(ref, ShellDestination.attendance),
            ),
        ],
      ),
      embedded: true,
      child: StaggeredColumn(
        children: [
          const _TodayMetrics(),
          if (isTeacher) const _TeacherTodo() else const _StudentTodo(),
          const _NextLessons(),
        ],
      ),
    );
  }

  static String _greeting(BuildContext context, String name) {
    final s = S.of(context);
    final hour = DateTime.now().hour;
    final part = hour < 12 ? s.goodMorning : (hour < 17 ? s.goodAfternoon : s.goodEvening);
    final first = name.split(' ').first;
    return first.isEmpty ? part : '$part, $first';
  }
}

class _TodayMetrics extends ConsumerWidget {
  const _TodayMetrics();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final sessions = ref.watch(_todaySessionsProvider);
    final assignments = ref.watch(_forMeAssignmentsProvider);

    final openRegisters = sessions.maybeWhen(
      data: (rows) => rows.where((r) => '${r['status']}' == 'open').length,
      orElse: () => 0,
    );
    final due = assignments.maybeWhen(
      data: (rows) => rows
          .where((r) => !{'submitted', 'graded'}.contains('${r['submissionState']}'))
          .length,
      orElse: () => 0,
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 900 ? 3 : (constraints.maxWidth >= 560 ? 2 : 1);
        final cards = [
          MetricCard(
            label: s.attendanceNotTaken,
            value: '$openRegisters',
            numericValue: openRegisters.toDouble(),
            icon: Icons.fact_check_outlined,
            hint: openRegisters == 0 ? s.upToDate : s.classesNeedAttendance(openRegisters),
            status: openRegisters == 0 ? StatusKind.success : StatusKind.warning,
            accent: openRegisters == 0 ? AppGradients.teal : AppGradients.attention,
          ),
          MetricCard(
            label: s.dueSoon,
            value: '$due',
            numericValue: due.toDouble(),
            icon: Icons.assignment_outlined,
            hint: s.dueSoon,
            status: due == 0 ? StatusKind.success : StatusKind.pending,
            accent: AppGradients.teal,
          ),
          MetricCard(
            label: s.workToMark,
            value: '—',
            icon: Icons.grading_outlined,
            hint: s.marksEntry,
          ),
        ];
        return GridView.count(
          crossAxisCount: columns,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: AppSpacing.s12,
          crossAxisSpacing: AppSpacing.s12,
          childAspectRatio: columns == 1 ? 3.4 : 2.2,
          children: cards,
        );
      },
    );
  }
}

class _TeacherTodo extends ConsumerWidget {
  const _TeacherTodo();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final sessions = ref.watch(_todaySessionsProvider);
    return SectionCard(
      title: s.whatToDoNext,
      subtitle: s.attendanceNotTaken,
      child: AsyncView<List<Map<String, dynamic>>>(
        value: sessions,
        onRetry: () => ref.invalidate(_todaySessionsProvider),
        isEmpty: (rows) => rows.isEmpty,
        emptyMessage: s.emptyList,
        builder: (rows) => Column(
          children: [
            for (final row in rows)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: Icon(
                  '${row['status']}' == 'open' ? Icons.lock_open : Icons.lock_outline,
                  color: '${row['status']}' == 'open' ? context.tokens.warning : context.tokens.success,
                ),
                title: Text('${row['sessionType']} · ${row['sessionDate']}'),
                subtitle: Text('${row['notes'] ?? ''}'),
                trailing: StatusChip(
                  label: '${row['status']}',
                  kind: '${row['status']}' == 'open' ? StatusKind.warning : StatusKind.success,
                  icon: '${row['status']}' == 'open' ? Icons.error_outline : Icons.check_circle_outline,
                ),
                onTap: () => context.push(AppRoutes.attendanceRegisterLocation('${row['id']}')),
              ),
          ],
        ),
      ),
    );
  }
}

class _StudentTodo extends ConsumerWidget {
  const _StudentTodo();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final assignments = ref.watch(_forMeAssignmentsProvider);
    return SectionCard(
      title: s.whatToDoNext,
      subtitle: s.dueSoon,
      child: AsyncView<List<Map<String, dynamic>>>(
        value: assignments,
        onRetry: () => ref.invalidate(_forMeAssignmentsProvider),
        isEmpty: (rows) => rows
            .where((r) => !{'submitted', 'graded'}.contains('${r['submissionState']}'))
            .isEmpty,
        emptyMessage: s.emptyList,
        builder: (rows) {
          final pending = rows
              .where((r) => !{'submitted', 'graded'}.contains('${r['submissionState']}'))
              .toList();
          return Column(
            children: [
              for (final item in pending)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.assignment_outlined),
                  title: Text('${item['title']}'),
                  subtitle: Text('${item['dueAt'] ?? ''}'),
                  trailing: StatusChip(
                    label: '${item['submissionState'] ?? s.pendingChanges}',
                    kind: StatusKind.pending,
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _NextLessons extends ConsumerWidget {
  const _NextLessons();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final published = ref.watch(_publishedTimetableProvider);
    return SectionCard(
      title: s.nextLessons,
      subtitle: s.timetable,
      child: AsyncView<List<Map<String, dynamic>>>(
        value: published,
        onRetry: () => ref.invalidate(_publishedTimetableProvider),
        isEmpty: (rows) => rows.isEmpty,
        emptyMessage: s.emptyList,
        builder: (rows) => Column(
          children: [
            for (final entry in rows.take(6))
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.schedule_outlined),
                title: Text('${entry['subjectName'] ?? entry['subject'] ?? s.subject}'),
                subtitle: Text(
                  [
                    if (entry['dayOfWeek'] != null) '${entry['dayOfWeek']}',
                    if (entry['periodStart'] != null) '${entry['periodStart']}',
                    if (entry['room'] != null) '${entry['room']}',
                  ].join(' · '),
                  style: context.texts.bodySmall,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

final _todaySessionsProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final now = DateTime.now();
  final date = '${now.year.toString().padLeft(4, '0')}-'
      '${now.month.toString().padLeft(2, '0')}-'
      '${now.day.toString().padLeft(2, '0')}';
  final data = await api.get('/api/v1/attendance/sessions', query: {'date': date});
  return (data as List).cast<Map<String, dynamic>>();
});

final _forMeAssignmentsProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments/for-me');
  return (data as List).cast<Map<String, dynamic>>();
});

final _publishedTimetableProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/timetable/published');
  return (data as List).cast<Map<String, dynamic>>();
});
