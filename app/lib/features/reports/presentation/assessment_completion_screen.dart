import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Assessment completion report: which assessments have marks entered vs missing.
class AssessmentCompletionScreen extends ConsumerStatefulWidget {
  const AssessmentCompletionScreen({super.key});

  @override
  ConsumerState<AssessmentCompletionScreen> createState() => _AssessmentCompletionScreenState();
}

class _AssessmentCompletionScreenState extends ConsumerState<AssessmentCompletionScreen> {
  String? _classGroupId;
  String? _termId;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_classGroupId != null) filters['classGroupId'] = _classGroupId;
    if (_termId != null) filters['termId'] = _termId;

    final report = ref.watch(assessmentCompletionProvider(filters));

    return ScreenScaffold(
      title: s.assessmentCompletion,
      description: s.assessmentCompletionDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            _SummaryCard(
              label: s.totalAssessments,
              value: '${data['assessments']?.length ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s8),
            Expanded(
              child: AccessibleChart(
                title: s.assessmentCompletionChart,
                summary: s.assessmentCompletionSummary,
                rows: _assessmentRows(data),
                chart: SizedBox(
                  height: 200,
                  child: SimpleBarChart(
                    series: _assessmentSeries(data),
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.assessment,
                    primary: true,
                    cell: (context, row) => Text('${row['assessmentTitle']}'),
                  ),
                  AppColumn(
                    label: s.totalStudents,
                    numeric: true,
                    cell: (context, row) => Text('${row['totalStudents']}'),
                  ),
                  AppColumn(
                    label: s.entered,
                    numeric: true,
                    cell: (context, row) => Text('${row['entered']}'),
                  ),
                  AppColumn(
                    label: s.missingMarks,
                    numeric: true,
                    cell: (context, row) => Text('${row['missing']}'),
                  ),
                  AppColumn(
                    label: s.completionRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['completionPercent']}%'),
                  ),
                ],
                rows: (data['assessments'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['assessmentId']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(assessmentCompletionProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _assessmentRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['assessments'] as List? ?? const [])) {
      rows.add(MapEntry('${row['assessmentTitle']}', '${row['completionPercent']}%'));
    }
    return rows;
  }

  List<MapEntry<String, double>> _assessmentSeries(Map<String, dynamic> data) {
    final List<MapEntry<String, double>> series = [];
    for (final row in (data['assessments'] as List? ?? const [])) {
      series.add(MapEntry('${row['assessmentTitle']}', (row['completionPercent'] as num? ?? 0).toDouble()));
    }
    return series;
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
