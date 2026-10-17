import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Student success & competency portfolio (spec: CBE, E-Learning, Examinations & Student Success).
class StudentSuccessScreen extends ConsumerStatefulWidget {
  const StudentSuccessScreen({super.key});
  @override
  ConsumerState<StudentSuccessScreen> createState() => _StudentSuccessScreenState();
}

class _StudentSuccessScreenState extends ConsumerState<StudentSuccessScreen> {
  String _section = 'progress';

  Map<String, String> _sections(S s) => {
        'progress': 'Progress',
        'competencies': 'Competencies',
        'timeline': 'Timeline',
        'interventions': 'Interventions',
      };

  String get _path => '/api/v1/student-success/$_section';

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
      case 'progress':
        return _progressList(context, rows);
      case 'competencies':
        return _competenciesList(context, rows);
      case 'timeline':
        return _timelineList(context, rows);
      default:
        return _interventionsList(context, rows);
    }
  }

  Widget _progressList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Student',
          cell: (ctx, r) => Text('${r['studentName'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Mastery',
          cell: (ctx, r) => Text('${r['masteryPercent'] ?? 0}%'),
          numeric: true,
          width: 100,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Evidence',
          cell: (ctx, r) => Text('${r['evidenceCount'] ?? 0}'),
          numeric: true,
          width: 100,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Interventions',
          cell: (ctx, r) => Text('${r['pendingInterventions'] ?? 0}'),
          numeric: true,
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id'] ?? r['studentId']}',
      searchHint: 'Search learners',
      empty: const EmptyState(
        icon: Icons.insights_outlined,
        title: 'No progress records yet',
        message: 'Progress appears once evidence is collected.',
      ),
    );
  }

  Widget _competenciesList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Competency',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'NOT_ASSESSED'}',
            kind: _statusKind('${r['status'] ?? 'NOT_ASSESSED'}'),
          ),
          width: 140,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search competencies',
      empty: const EmptyState(
        icon: Icons.track_changes_outlined,
        title: 'No competency evidence yet',
        message: 'Evidence is recorded from assessments and activities.',
      ),
    );
  }

  Widget _timelineList(BuildContext context, List<Map<String, dynamic>> rows) {
    return ListView.builder(
      padding: const EdgeInsets.all(AppSpacing.s16),
      itemCount: rows.length,
      itemBuilder: (context, index) {
        final row = rows[index];
        return ListTile(
          leading: const Icon(Icons.timeline_outlined),
          title: Text('${row['title'] ?? row['event'] ?? ''}'),
          subtitle: Text('${row['occurredAt'] ?? row['date'] ?? ''}'),
        );
      },
    );
  }

  Widget _interventionsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Goal',
          cell: (ctx, r) => Text('${r['goal'] ?? ''}'),
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
          label: 'Review date',
          cell: (ctx, r) => Text('${r['reviewDate'] ?? ''}'),
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search interventions',
      empty: const EmptyState(
        icon: Icons.support_agent_outlined,
        title: 'No interventions yet',
        message: 'Create an intervention plan to support a learner.',
      ),
    );
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'COMPLETED':
      case 'MASTERED':
        return StatusKind.success;
      case 'ACTIVE':
      case 'UNDER_REVIEW':
        return StatusKind.pending;
      case 'DRAFT':
        return StatusKind.neutral;
      case 'CANCELLED':
        return StatusKind.warning;
      default:
        return StatusKind.info;
    }
  }
}
