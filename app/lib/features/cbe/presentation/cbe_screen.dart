import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// CBE curriculum workspace (spec: CBE, E-Learning, Examinations & Student Success).
///
/// Top-level screen: browse curriculum versions, drill into the competency
/// hierarchy, and open the rubric builder and scheme of work.
class CbeScreen extends ConsumerStatefulWidget {
  const CbeScreen({super.key});
  @override
  ConsumerState<CbeScreen> createState() => _CbeScreenState();
}

class _CbeScreenState extends ConsumerState<CbeScreen> {
  String _section = 'curricula';

  Map<String, String> _sections(S s) => {
        'curricula': 'Curricula',
        'competencies': 'Competencies',
        'rubrics': 'Rubrics',
        'learning-plans': 'Learning plans',
      };

  String get _path => '/api/v1/cbe/$_section';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final manager = ref.watch(sessionProvider).can('academics.manage');
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
                  onSelected: (_) => setState(() {
                        _section = section.key;
                      }),
                ),
              ),
          ]),
        ),
        Expanded(
          child: AsyncView<List<Map<String, dynamic>>>(
            value: ref.watch(provider),
            onRetry: () => ref.invalidate(provider),
            isEmpty: (rows) => rows.isEmpty,
            emptyMessage: 'No records yet. Create one to get started.',
            builder: (rows) => RefreshIndicator(
              onRefresh: () => ref.refresh(provider.future),
              child: _buildList(context, rows, manager),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _buildList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    switch (_section) {
      case 'curricula':
        return _curriculaList(context, rows, manager);
      case 'competencies':
        return _competenciesList(context, rows, manager);
      case 'rubrics':
        return _rubricsList(context, rows, manager);
      default:
        return _plansList(context, rows, manager);
    }
  }

  Widget _curriculaList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    final columns = [
      AppColumn<Map<String, dynamic>>(
        label: 'Code',
        cell: (ctx, r) => Text('${r['code'] ?? ''}', style: ctx.texts.bodyMedium),
        primary: true,
        width: 120,
      ),
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
        label: 'Version',
        cell: (ctx, r) => Text('${r['version'] ?? 1}'),
        numeric: true,
        width: 80,
      ),
    ];
    return AppDataTable<Map<String, dynamic>>(
      columns: columns,
      rows: rows,
      rowKey: (r) => '${r['id']}',
      onTap: (r) => _openCurriculum(context, r),
      searchHint: 'Search curricula',
      empty: EmptyState(
        icon: Icons.menu_book_outlined,
        title: 'No curricula yet',
        message: 'Create a curriculum version to start mapping competencies.',
        action: manager
            ? FilledButton.icon(
                onPressed: () => _createCurriculum(context),
                icon: const Icon(Icons.add),
                label: const Text('New curriculum'),
              )
            : null,
      ),
      header: manager
          ? Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.s8),
              child: Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  onPressed: () => _createCurriculum(context),
                  icon: const Icon(Icons.add),
                  label: const Text('New curriculum'),
                ),
              ),
            )
          : null,
    );
  }

  Widget _competenciesList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Code',
          cell: (ctx, r) => Text('${r['code'] ?? ''}'),
          primary: true,
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? ''}'),
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
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search competencies',
      empty: EmptyState(
        icon: Icons.track_changes_outlined,
        title: 'No competencies yet',
        message: 'Competencies are created within a curriculum version.',
      ),
    );
  }

  Widget _rubricsList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Criteria',
          cell: (ctx, r) => Text('${(r['criteria'] as List?)?.length ?? 0}'),
          numeric: true,
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search rubrics',
      empty: EmptyState(
        icon: Icons.rule_outlined,
        title: 'No rubrics yet',
        message: 'Create a rubric to define assessment criteria and performance levels.',
        action: manager
            ? FilledButton.icon(
                onPressed: () => _createRubric(context),
                icon: const Icon(Icons.add),
                label: const Text('New rubric'),
              )
            : null,
      ),
      header: manager
          ? Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.s8),
              child: Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  onPressed: () => _createRubric(context),
                  icon: const Icon(Icons.add),
                  label: const Text('New rubric'),
                ),
              ),
            )
          : null,
    );
  }

  Widget _plansList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
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
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search learning plans',
      empty: EmptyState(
        icon: Icons.calendar_today_outlined,
        title: 'No learning plans yet',
        message: 'Map curriculum outcomes to weekly lessons and assessments.',
      ),
    );
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'PUBLISHED':
      case 'APPROVED':
        return StatusKind.success;
      case 'IN_REVIEW':
      case 'PENDING':
        return StatusKind.pending;
      case 'DRAFT':
        return StatusKind.neutral;
      case 'RETIRED':
      case 'ARCHIVED':
        return StatusKind.warning;
      default:
        return StatusKind.info;
    }
  }

  void _openCurriculum(BuildContext context, Map<String, dynamic> row) {
    // Navigate to curriculum detail / competency explorer.
  }

  void _createCurriculum(BuildContext context) {
    // Open create-curriculum dialog.
  }

  void _createRubric(BuildContext context) {
    // Open rubric builder.
  }
}
