import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import 'grades_providers.dart';

/// Class gradebook grid: students as rows, assessments as columns.
class GradebookScreen extends ConsumerStatefulWidget {
  const GradebookScreen({super.key, required this.classId});

  final String classId;

  @override
  ConsumerState<GradebookScreen> createState() => _GradebookScreenState();
}

class _GradebookScreenState extends ConsumerState<GradebookScreen> {
  String? _termId;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final canEnter = session.can('grade.enter');
    final canPublish = session.can('grade.publish');

    final filters = <String, dynamic>{'classId': widget.classId, if (_termId != null) 'termId': _termId};
    final gradebook = ref.watch(classGradebookProvider(filters));

    return ScreenScaffold(
      title: s.gradebook,
      description: s.assessmentDescription,
      embedded: true,
      primaryAction: canEnter
          ? AppButton(
              label: s.createAssessment,
              variant: AppButtonVariant.primary,
              icon: Icons.add,
              onPressed: () => context.push(
                AppRoutes.assessmentEntry,
                extra: widget.classId,
              ),
            )
          : null,
      child: gradebook.when(
        data: (rows) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (rows.isEmpty)
              const EmptyState(message: 'No students found in this class.'),
            if (rows.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.s12),
              Expanded(
                child: _GradebookGrid(
                  rows: rows,
                  canEnter: canEnter,
                  canPublish: canPublish,
                  onEntryTap: (assessmentId, entryId, studentId) {
                    context.push(
                      AppRoutes.marksEntry,
                      extra: {'assessmentId': assessmentId, 'entryId': entryId, 'studentId': studentId},
                    );
                  },
                  onPublish: (assessmentId) async {
                    try {
                      await ref.read(apiClientProvider).post(
                            '/api/v1/grades/assessments/$assessmentId/publish',
                          );
                      ref.invalidate(classGradebookProvider(filters));
                      if (mounted) {
                        showAppSnack(context, s.published, kind: StatusKind.success);
                      }
                    } on Object catch (e) {
                      showApiError(context, e);
                    }
                  },
                ),
              ),
            ],
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(classGradebookProvider(filters))),
      ),
    );
  }
}

/// Grid of students x assessments with inline mark entry.
class _GradebookGrid extends StatelessWidget {
  const _GradebookGrid({
    required this.rows,
    required this.canEnter,
    required this.canPublish,
    required this.onEntryTap,
    required this.onPublish,
  });

  final List<Map<String, dynamic>> rows;
  final bool canEnter;
  final bool canPublish;
  final void Function(String assessmentId, String? entryId, String studentId) onEntryTap;
  final void Function(String assessmentId) onPublish;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final first = rows.isNotEmpty ? rows.first : <String, dynamic>{};
    final entries = (first['entries'] as List? ?? const <Map<String, dynamic>>[])
        .cast<Map<String, dynamic>>();
    final assessments = <Map<String, dynamic>>[];
    for (final e in entries) {
      assessments.add({
        'id': '${e['assessmentId'] ?? e['id']}',
        'title': '${e['assessmentTitle'] ?? e['title'] ?? s.untitled}',
        'maxMarks': e['maxMarks'] ?? 0,
      });
    }

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          minWidth: MediaQuery.sizeOf(context).width.clamp(0, 1400).toDouble(),
        ),
        child: DataTable(
          headingRowHeight: 44,
          dataRowMinHeight: 48,
          dataRowMaxHeight: 52,
          columns: [
            DataColumn(label: Text(s.student, style: context.texts.labelMedium)),
            for (final a in assessments)
              DataColumn(
                numeric: true,
                label: SizedBox(
                  width: 96,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('${a['title']}',
                          style: context.texts.labelMedium,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis),
                      Text('/${a['maxMarks']}',
                          style: context.texts.labelSmall?.copyWith(
                            color: context.tokens.textSecondary,
                          )),
                    ],
                  ),
                ),
              ),
            if (canPublish)
              DataColumn(
                label: SizedBox(
                  width: 96,
                  child: Center(
                    child: Text(s.publish,
                        style: context.texts.labelMedium),
                  ),
                ),
              ),
          ],
          rows: [
            for (final row in rows)
              DataRow(
                key: ValueKey('${row['studentId']}'),
                cells: [
                  DataCell(
                    Text('${row['studentName']} (${row['admissionNumber'] ?? ''})',
                        style: context.texts.bodyMedium),
                  ),
                  for (final a in assessments) ...[
                    DataCell(
                      _MarkCell(
                        assessmentId: '${a['id']}',
                        studentId: '${row['studentId']}',
                        entries: (row['entries'] as List? ?? const [])
                            .cast<Map<String, dynamic>>(),
                        canEnter: canEnter,
                        onTap: () => onEntryTap('${a['id']}', null, '${row['studentId']}'),
                      ),
                    ),
                    if (canPublish)
                      DataCell(
                        _PublishCell(
                          assessmentId: '${a['id']}',
                          onPublish: () => onPublish('${a['id']}'),
                        ),
                      ),
                  ],
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _MarkCell extends StatelessWidget {
  const _MarkCell({
    required this.assessmentId,
    required this.studentId,
    required this.entries,
    required this.canEnter,
    required this.onTap,
  });

  final String assessmentId;
  final String studentId;
  final List<Map<String, dynamic>> entries;
  final bool canEnter;
  final VoidCallback onTap;

  Map<String, dynamic>? get _entry {
    try {
      return entries.firstWhere((e) => '${e['assessmentId']}' == assessmentId);
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final entry = _entry;
    final marks = entry?['marksObtained'];
    final grade = entry?['grade'];
    final status = entry?['status'] ?? 'draft';
    final pending = status == 'draft' || status == 'submitted';

    return GestureDetector(
      onTap: canEnter ? onTap : null,
      child: Container(
        alignment: Alignment.center,
        padding: const EdgeInsets.symmetric(horizontal: 8),
        decoration: BoxDecoration(
          color: pending
              ? context.scheme.surfaceContainerHighest.withValues(alpha: 0.5)
              : null,
          border: Border.all(color: context.tokens.border),
          borderRadius: BorderRadius.circular(AppRadius.sm),
        ),
        child: Text(
          marks == null ? (pending ? '—' : '') : '$marks${grade != null ? ' ($grade)' : ''}',
          style: context.texts.bodyMedium,
        ),
      ),
    );
  }
}

class _PublishCell extends StatelessWidget {
  const _PublishCell({
    required this.assessmentId,
    required this.onPublish,
  });

  final String assessmentId;
  final VoidCallback onPublish;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: AppButton(
        label: 'Publish',
        variant: AppButtonVariant.outline,
        icon: Icons.publish,
        onPressed: onPublish,
      ),
    );
  }
}
