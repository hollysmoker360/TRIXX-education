import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Evidence coverage report: per-student competency evidence coverage.
class EvidenceCoverageScreen extends ConsumerStatefulWidget {
  const EvidenceCoverageScreen({super.key});

  @override
  ConsumerState<EvidenceCoverageScreen> createState() => _EvidenceCoverageScreenState();
}

class _EvidenceCoverageScreenState extends ConsumerState<EvidenceCoverageScreen> {
  String? _classGroupId;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_classGroupId != null) filters['classGroupId'] = _classGroupId;

    final report = ref.watch(evidenceCoverageProvider(filters));

    return ScreenScaffold(
      title: s.evidenceCoverage,
      description: s.evidenceCoverageDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            _SummaryCard(
              label: s.studentsWithEvidence,
              value: '${data['students']?.length ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.student,
                    primary: true,
                    cell: (context, row) => Text('${row['studentName']}'),
                  ),
                  AppColumn(
                    label: s.totalCompetencies,
                    numeric: true,
                    cell: (context, row) => Text('${row['totalCompetencies']}'),
                  ),
                  AppColumn(
                    label: s.withEvidence,
                    numeric: true,
                    cell: (context, row) => Text('${row['withEvidence']}'),
                  ),
                  AppColumn(
                    label: s.coverageRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['coveragePercent']}%'),
                  ),
                ],
                rows: (data['students'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['studentId']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(evidenceCoverageProvider(filters))),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(value,
                style: context.texts.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            Text(label, style: context.texts.bodySmall?.copyWith(color: context.tokens.textSecondary)),
          ],
        ),
      ),
    );
  }
}
