import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// E-learning workspace (spec: CBE, E-Learning, Examinations & Student Success).
class ELearningScreen extends ConsumerStatefulWidget {
  const ELearningScreen({super.key});
  @override
  ConsumerState<ELearningScreen> createState() => _ELearningScreenState();
}

class _ELearningScreenState extends ConsumerState<ELearningScreen> {
  String _section = 'courses';

  Map<String, String> _sections(S s) => {
        'courses': 'Courses',
        'assignments': 'Assignments',
        'resources': 'Resources',
      };

  String get _path => '/api/v1/elearning/$_section';

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
      case 'courses':
        return _coursesList(context, rows);
      case 'assignments':
        return _assignmentsList(context, rows);
      default:
        return _resourcesList(context, rows);
    }
  }

  Widget _coursesList(BuildContext context, List<Map<String, dynamic>> rows) {
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
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Progress',
          cell: (ctx, r) => Text('${r['progress'] ?? 0}%'),
          numeric: true,
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search courses',
      empty: const EmptyState(
        icon: Icons.menu_book_outlined,
        title: 'No courses yet',
        message: 'Create a course to start publishing lessons.',
      ),
    );
  }

  Widget _assignmentsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Due',
          cell: (ctx, r) => Text('${r['dueAt'] ?? ''}'),
          width: 160,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'State',
          cell: (ctx, r) => StatusChip(
            label: '${r['submissionState'] ?? 'NOT_SUBMITTED'}',
            kind: _submissionKind('${r['submissionState'] ?? 'NOT_SUBMITTED'}'),
          ),
          width: 140,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search assignments',
      empty: const EmptyState(
        icon: Icons.assignment_outlined,
        title: 'No assignments yet',
        message: 'Assignments appear here once published for your classes.',
      ),
    );
  }

  Widget _resourcesList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Type',
          cell: (ctx, r) => Text('${r['type'] ?? ''}'),
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search resources',
      empty: const EmptyState(
        icon: Icons.folder_outlined,
        title: 'No resources yet',
        message: 'Upload learning materials for your classes.',
      ),
    );
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'PUBLISHED':
      case 'APPROVED':
        return StatusKind.success;
      case 'IN_REVIEW':
        return StatusKind.pending;
      case 'DRAFT':
        return StatusKind.neutral;
      default:
        return StatusKind.info;
    }
  }

  StatusKind _submissionKind(String state) {
    switch (state.toUpperCase()) {
      case 'GRADED':
      case 'SUBMITTED':
        return StatusKind.success;
      case 'DRAFT':
        return StatusKind.neutral;
      case 'OVERDUE':
        return StatusKind.error;
      default:
        return StatusKind.pending;
    }
  }
}
