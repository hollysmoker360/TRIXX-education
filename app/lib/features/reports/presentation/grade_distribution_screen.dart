import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Grade distribution report: bar chart of grades for a class/subject.
class GradeDistributionScreen extends ConsumerStatefulWidget {
  const GradeDistributionScreen({super.key});

  @override
  ConsumerState<GradeDistributionScreen> createState() => _GradeDistributionScreenState();
}

class _GradeDistributionScreenState extends ConsumerState<GradeDistributionScreen> {
  String? _classGroupId;
  String? _subjectId;
  String? _from;
  String? _to;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);

    final filters = <String, dynamic>{};
    if (_classGroupId != null) filters['classGroupId'] = _classGroupId;
    if (_subjectId != null) filters['subjectId'] = _subjectId;
    if (_from != null) filters['from'] = _from;
    if (_to != null) filters['to'] = _to;

    final report = ref.watch(gradeDistributionProvider(filters));

    return ScreenScaffold(
      title: s.gradeDistribution,
      description: s.gradeDistributionDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            _SummaryCard(
              label: s.totalStudents,
              value: '${data['totalStudents'] ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s8),
            _SummaryCard(
              label: s.averageScore,
              value: '${data['average'] ?? 0}%',
            ),
            const SizedBox(height: AppSpacing.s8),
            _SummaryCard(
              label: s.medianScore,
              value: '${data['median'] ?? 0}%',
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AccessibleChart(
                title: s.gradeDistributionChart,
                summary: s.gradeDistributionSummary,
                rows: _distributionRows(data),
                chart: SizedBox(
                  height: 200,
                  child: SimpleBarChart(
                    series: _distributionSeries(data),
                    unit: '%',
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.grade,
                    primary: true,
                    cell: (context, row) => Text('${row['grade']}'),
                  ),
                  AppColumn(
                    label: s.count,
                    numeric: true,
                    cell: (context, row) => Text('${row['count']}'),
                  ),
                  AppColumn(
                    label: s.percent,
                    numeric: true,
                    cell: (context, row) => Text('${row['percent']}%'),
                  ),
                ],
                rows: (data['distribution'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['grade']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(gradeDistributionProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _distributionRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['distribution'] as List? ?? const [])) {
      rows.add(MapEntry('${row['grade']}', '${row['count']}'));
    }
    return rows;
  }

  List<MapEntry<String, double>> _distributionSeries(Map<String, dynamic> data) {
    final List<MapEntry<String, double>> series = [];
    for (final row in (data['distribution'] as List? ?? const [])) {
      series.add(MapEntry('${row['grade']}', (row['percent'] as num? ?? 0).toDouble()));
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
