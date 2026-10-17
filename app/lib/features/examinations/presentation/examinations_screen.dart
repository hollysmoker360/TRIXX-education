import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Examinations and assessment workspace (spec: CBE, E-Learning, Examinations & Student Success).
class ExaminationsScreen extends ConsumerStatefulWidget {
  const ExaminationsScreen({super.key});
  @override
  ConsumerState<ExaminationsScreen> createState() => _ExaminationsScreenState();
}

class _ExaminationsScreenState extends ConsumerState<ExaminationsScreen> {
  String _section = 'assessments';

  Map<String, String> _sections(S s) => {
        'assessments': 'Assessments',
        'exam-sessions': 'Exam sessions',
        'marking-queue': 'Marking queue',
        'results': 'Results',
      };

  String get _path => '/api/v1/examinations/$_section';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final provider = recordsProvider(_path);
    return Scaffold(
      body: Column(children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.all(AppSpacing.s16),
          child: Row(children: [
            for (final section in _sections(s).entries)
              Padding(
                padding: const EdgeInsets.only(right: AppSpacing.s8),
                child: ChoiceChip(
                  label: Text(section.value),
                  selected: _section == section.key,
                  onSelected: (_) => setState(() => _section = section.key),
                ),
              ),
          ]),
        ),
        Expanded(
          child: AsyncView<List<Map<String, dynamic>>>(
            value: ref.watch(provider),
            onRetry: () => ref.invalidate(provider),
            isEmpty: (rows) => rows.isEmpty,
            emptyMessage: 'No records yet.',
            builder: (rows) => RefreshIndicator(
              onRefresh: () => ref.refresh(provider.future),
              child: _buildList(context, rows),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _buildList(BuildContext context, List<Map<String, dynamic>> rows) {
    switch (_section) {
      case 'assessments':
        return _assessmentsList(context, rows);
      case 'exam-sessions':
        return _sessionsList(context, rows);
      case 'marking-queue':
        return _markingList(context, rows);
      default:
        return _resultsList(context, rows);
    }
  }

  Widget _assessmentsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'DRAFT'}',
            kind: _statusKind('${r['status'] ?? 'DRAFT'}'),
          ),
          width: 130,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Marks',
          cell: (ctx, r) => Text('${r['totalMarks'] ?? ''}'),
          numeric: true,
          width: 80,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Duration',
          cell: (ctx, r) => Text('${r['durationMinutes'] ?? ''} min'),
          numeric: true,
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search assessments',
      empty: const EmptyState(
        icon: Icons.quiz_outlined,
        title: 'No assessments yet',
        message: 'Create an assessment to start building exams.',
      ),
    );
  }

  Widget _sessionsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'SCHEDULED'}',
            kind: _statusKind('${r['status'] ?? 'SCHEDULED'}'),
          ),
          width: 130,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Starts',
          cell: (ctx, r) => Text('${r['startsAt'] ?? ''}'),
          width: 160,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search exam sessions',
      empty: const EmptyState(
        icon: Icons.event_outlined,
        title: 'No exam sessions yet',
        message: 'Schedule an exam session for eligible candidates.',
      ),
    );
  }

  Widget _markingList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Assessment',
          cell: (ctx, r) => Text('${r['assessmentTitle'] ?? r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Candidate',
          cell: (ctx, r) => Text('${r['candidateName'] ?? r['studentName'] ?? ''}'),
          width: 160,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'PENDING'}',
            kind: _statusKind('${r['status'] ?? 'PENDING'}'),
          ),
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search marking tasks',
      empty: const EmptyState(
        icon: Icons.grading_outlined,
        title: 'No marking tasks',
        message: 'Marking tasks appear here when exams are submitted.',
      ),
    );
  }

  Widget _resultsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Assessment',
          cell: (ctx, r) => Text('${r['assessmentTitle'] ?? r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'PENDING_MARKING'}',
            kind: _statusKind('${r['status'] ?? 'PENDING_MARKING'}'),
          ),
          width: 140,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search results',
      empty: const EmptyState(
        icon: Icons.assessment_outlined,
        title: 'No results yet',
        message: 'Results appear after marking and moderation.',
      ),
    );
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'PUBLISHED':
      case 'APPROVED':
      case 'CLOSED':
        return StatusKind.success;
      case 'IN_REVIEW':
      case 'SCHEDULED':
      case 'PENDING':
      case 'PENDING_MARKING':
        return StatusKind.pending;
      case 'DRAFT':
        return StatusKind.neutral;
      default:
        return StatusKind.info;
    }
  }
}
