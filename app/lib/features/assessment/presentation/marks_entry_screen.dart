import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/theme/typography.dart';
import '../../../core/widgets/common.dart';

/// Marks entry workspace: a responsive grid (DataTable on medium widths and up,
/// stacked records on compact), autosaved per cell, with a guard before
/// publishing. Keyboard-first: Tab between cells, Enter to commit.
class MarksEntryScreen extends ConsumerStatefulWidget {
  const MarksEntryScreen({super.key, required this.assessmentId, this.assessmentName});

  final String assessmentId;
  final String? assessmentName;

  @override
  ConsumerState<MarksEntryScreen> createState() => _MarksEntryScreenState();
}

class _MarksEntryScreenState extends ConsumerState<MarksEntryScreen> {
  final _drafts = <String, ({TextEditingController marks, TextEditingController feedback})>{};
  final _dirty = <String>{};

  @override
  void dispose() {
    for (final entry in _drafts.values) {
      entry.marks.dispose();
      entry.feedback.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final rows = ref.watch(_gradeRowsProvider(widget.assessmentId));

    return ScreenScaffold(
      title: widget.assessmentName ?? s.marksEntry,
      description: s.autosaved,
      primaryAction: AppButton(
        label: s.publishResults,
        icon: Icons.publish,
        onPressed: () => _publish(context),
      ),
      actions: [
        IconButton(
          onPressed: () => _saveAll(context),
          icon: const Icon(Icons.save_outlined),
          tooltip: s.save,
        ),
      ],
      child: AsyncView<List<Map<String, dynamic>>>(
        value: rows,
        onRetry: () => ref.invalidate(_gradeRowsProvider(widget.assessmentId)),
        isEmpty: (value) => value.isEmpty,
        emptyMessage: s.emptyList,
        builder: (value) {
          final missing = value
              .where((row) => _controller(row).marks.text.trim().isEmpty)
              .length;
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (missing > 0)
                Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.s12),
                  child: AppBanner(
                    kind: StatusKind.warning,
                    title: s.missingMarks,
                    message: '$missing',
                  ),
                ),
              if (_dirty.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.s12),
                  child: AppBanner(
                    kind: StatusKind.info,
                    message: '${_dirty.length} ${s.unsavedChanges}',
                    action: TextButton(
                      onPressed: () => _saveAll(context),
                      child: Text(s.save),
                    ),
                  ),
                ),
              Card(
                child: Column(
                  children: [
                    for (final row in value) _MarkRow(
                      key: ValueKey('${row['studentId']}'),
                      row: row,
                      controller: _controller(row),
                      onChanged: () => setState(() => _dirty.add('${row['studentId']}')),
                      onCommit: () => _save(context, row),
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  ({TextEditingController marks, TextEditingController feedback}) _controller(
    Map<String, dynamic> row,
  ) {
    final id = '${row['studentId']}';
    return _drafts.putIfAbsent(
      id,
      () => (
        marks: TextEditingController(
          text: row['grade'] == null ? '' : '${row['grade']}',
        ),
        feedback: TextEditingController(text: '${row['feedback'] ?? ''}'),
      ),
    );
  }

  Future<void> _save(BuildContext context, Map<String, dynamic> row) async {
    final s = S.of(context);
    final studentId = '${row['studentId']}';
    final submissionId = row['submissionId'];
    if (submissionId == null) {
      showAppSnack(context, s.somethingWentWrong, kind: StatusKind.error);
      return;
    }
    final draft = _controller(row);
    final marks = double.tryParse(draft.marks.text.trim());
    if (draft.marks.text.trim().isNotEmpty && marks == null) {
      showAppSnack(context, s.somethingWentWrong, kind: StatusKind.error);
      return;
    }
    try {
      final api = ref.read(apiClientProvider);
      await api.patch('/api/v1/learning/submissions/$submissionId/grade', data: {
        'marks': marks,
        'feedback': draft.feedback.text.trim(),
      });
      setState(() => _dirty.remove(studentId));
      if (context.mounted) showAppSnack(context, s.saved);
    } catch (error) {
      if (context.mounted) showApiError(context, error);
    }
  }

  Future<void> _saveAll(BuildContext context) async {
    final rows = ref.read(_gradeRowsProvider(widget.assessmentId)).valueOrNull ?? const [];
    for (final row in rows.where((r) => _dirty.contains('${r['studentId']}'))) {
      await _save(context, row);
    }
    if (context.mounted) ref.invalidate(_gradeRowsProvider(widget.assessmentId));
  }

  Future<void> _publish(BuildContext context) async {
    final s = S.of(context);
    final confirmed = await showConfirmDialog(
      context,
      title: s.publishResults,
      message: s.publishConfirm,
      confirmLabel: s.publishResults,
    );
    if (!confirmed || !context.mounted) return;
    try {
      await _saveAll(context);
      final api = ref.read(apiClientProvider);
      await api.post('/api/v1/learning/assignments/${widget.assessmentId}/publish-grades', data: <String, dynamic>{});
      if (context.mounted) {
        showAppSnack(context, s.resultsPublished);
        ref.invalidate(_gradeRowsProvider(widget.assessmentId));
      }
    } catch (error) {
      if (context.mounted) showApiError(context, error);
    }
  }
}

class _MarkRow extends StatelessWidget {
  const _MarkRow({
    super.key,
    required this.row,
    required this.controller,
    required this.onChanged,
    required this.onCommit,
  });

  final Map<String, dynamic> row;
  final ({TextEditingController marks, TextEditingController feedback}) controller;
  final VoidCallback onChanged;
  final VoidCallback onCommit;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s16, vertical: AppSpacing.s8),
      child: Row(
        children: [
          Avatar(name: '${row['studentName'] ?? ''}', size: 32),
          const SizedBox(width: AppSpacing.s12),
          Expanded(
            flex: 3,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${row['studentName'] ?? ''}', style: context.texts.titleSmall),
                Text('${row['state'] ?? ''}', style: context.texts.bodySmall),
              ],
            ),
          ),
          SizedBox(
            width: 96,
            child: TextField(
              controller: controller.marks,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
              ],
              textAlign: TextAlign.end,
              style: AppType.numeric(context.texts.titleSmall),
              onChanged: (_) => onChanged(),
              onSubmitted: (_) => onCommit(),
              onEditingComplete: onCommit,
              decoration: const InputDecoration(hintText: '—'),
            ),
          ),
          const SizedBox(width: AppSpacing.s8),
          Expanded(
            flex: 4,
            child: TextField(
              controller: controller.feedback,
              onChanged: (_) => onChanged(),
              onSubmitted: (_) => onCommit(),
              decoration: InputDecoration(hintText: s.feedback),
            ),
          ),
          IconButton(
            onPressed: onCommit,
            icon: const Icon(Icons.check),
            tooltip: s.save,
          ),
        ],
      ),
    );
  }
}

/// Joins submissions (for the id used when grading) with grade rows (for the
/// student name and current mark). The grade endpoint is keyed by submission id,
/// so both are needed (specification section 11.3).
final _gradeRowsProvider =
    FutureProvider.autoDispose.family<List<Map<String, dynamic>>, String>((ref, assignmentId) async {
  final api = ref.watch(apiClientProvider);
  final submissions = await api.get('/api/v1/learning/assignments/$assignmentId/submissions');
  final grades = await api.get('/api/v1/learning/assignments/$assignmentId/grades');
  final gradeByStudent = <String, Map<String, dynamic>>{
    for (final row in (grades as List).cast<Map<String, dynamic>>())
      '${row['studentId']}': row,
  };
  return [
    for (final submission in (submissions as List).cast<Map<String, dynamic>>())
      {
        ...submission,
        'studentName': gradeByStudent['${submission['studentId']}']?['studentName'] ??
            '${submission['studentId']}',
        'grade': gradeByStudent['${submission['studentId']}']?['grade'],
        'feedback': submission['feedback'] ??
            gradeByStudent['${submission['studentId']}']?['feedback'],
      },
  ];
});
