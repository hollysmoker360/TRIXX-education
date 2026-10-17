import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/theme/typography.dart';
import '../../../core/widgets/common.dart';

/// Marks and results hub. Teachers see assessments they can mark and publish;
/// learners see their own published grades. Built on the assignment grade
/// endpoints that exist today (specification section 11.3 — reconcile proposed
/// endpoints with the real controllers).
class AssessmentsScreen extends ConsumerWidget {
  const AssessmentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final canManage = session.can('grade.enter') || session.can('assignment.create');

    return ScreenScaffold(
      title: s.marks,
      description: canManage ? s.gradebook : s.reportCard,
      embedded: true,
      child: canManage ? const _TeacherAssessments() : const _LearnerGrades(),
    );
  }
}

class _TeacherAssessments extends ConsumerStatefulWidget {
  const _TeacherAssessments();

  @override
  ConsumerState<_TeacherAssessments> createState() => _TeacherAssessmentsState();
}

class _TeacherAssessmentsState extends ConsumerState<_TeacherAssessments> {
  String _status = '';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final assessments = ref.watch(_assessmentsProvider(_status));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilterBar(
          filters: [
            for (final state in const ['draft', 'published'])
              ChoiceChip(
                label: Text(state == 'draft' ? s.draft : s.published),
                selected: _status == state,
                onSelected: (selected) => setState(() => _status = selected ? state : ''),
              ),
          ],
          onClearAll: () => setState(() => _status = ''),
        ),
        AsyncView<List<Map<String, dynamic>>>(
          value: assessments,
          onRetry: () => ref.invalidate(_assessmentsProvider(_status)),
          isEmpty: (rows) => rows.isEmpty,
          emptyMessage: s.emptyList,
          builder: (rows) => AppDataTable<Map<String, dynamic>>(
            columns: [
              AppColumn(
                label: s.assignments,
                primary: true,
                cell: (context, row) => Text(
                  '${row['title']}',
                  style: context.texts.titleSmall,
                ),
              ),
              AppColumn(
                label: s.classGroup,
                cell: (context, row) => Text('${row['className'] ?? row['classGroupId'] ?? '—'}'),
              ),
              AppColumn(
                label: s.deadline,
                cell: (context, row) => Text('${row['dueAt'] ?? '—'}'),
              ),
              AppColumn(
                label: s.status,
                cell: (context, row) => StatusChip(
                  label: '${row['state'] ?? 'draft'}',
                  kind: '${row['state']}' == 'published' ? StatusKind.success : StatusKind.neutral,
                  icon: '${row['state']}' == 'published' ? Icons.publish : Icons.edit_note,
                ),
              ),
              AppColumn(
                label: s.marksEntry,
                cell: (context, row) => AppButton(
                  label: s.open,
                  variant: AppButtonVariant.outline,
                  onPressed: () => context.push(
                    AppRoutes.marksEntryLocation('${row['id']}'),
                    extra: '${row['title']}',
                  ),
                ),
              ),
            ],
            rows: rows,
            rowKey: (row) => '${row['id']}',
            onTap: (row) => context.push(
              AppRoutes.marksEntryLocation('${row['id']}'),
              extra: '${row['title']}',
            ),
            empty: EmptyState(message: s.emptyList),
          ),
        ),
      ],
    );
  }
}

class _LearnerGrades extends ConsumerWidget {
  const _LearnerGrades();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final grades = ref.watch(_myGradesProvider);
    return AsyncView<List<Map<String, dynamic>>>(
      value: grades,
      onRetry: () => ref.invalidate(_myGradesProvider),
      isEmpty: (rows) => rows.isEmpty,
      emptyMessage: s.emptyList,
      builder: (rows) => Card(
        child: Column(
          children: [
            for (final row in rows)
              ListTile(
                leading: const Icon(Icons.insights_outlined),
                title: Text('${row['title'] ?? row['assignmentTitle'] ?? s.assignments}'),
                subtitle: Text('${row['subjectName'] ?? row['className'] ?? ''}'),
                trailing: Text(
                  '${row['score'] ?? row['grade'] ?? '—'}',
                  style: AppType.numeric(context.texts.titleSmall).copyWith(
                    color: context.scheme.primary,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

final _assessmentsProvider =
    FutureProvider.autoDispose.family<List<Map<String, dynamic>>, String>((ref, status) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments', query: {
    'size': 100,
    if (status.isNotEmpty) 'state': status,
  });
  return (data as List).cast<Map<String, dynamic>>();
});

final _myGradesProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments/my-grades');
  return (data as List).cast<Map<String, dynamic>>();
});
