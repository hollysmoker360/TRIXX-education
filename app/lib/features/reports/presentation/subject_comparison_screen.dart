import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Subject comparison report: average performance by subject.
class SubjectComparisonScreen extends ConsumerStatefulWidget {
  const SubjectComparisonScreen({super.key});

  @override
  ConsumerState<SubjectComparisonScreen> createState() => _SubjectComparisonScreenState();
}

class _SubjectComparisonScreenState extends ConsumerState<SubjectComparisonScreen> {
  String? _classGroupId;
  String? _from;
  String? _to;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_classGroupId != null) filters['classGroupId'] = _classGroupId;
    if (_from != null) filters['from'] = _from;
    if (_to != null) filters['to'] = _to;

    final report = ref.watch(subjectComparisonProvider(filters));

    return ScreenScaffold(
      title: s.subjectComparison,
      description: s.subjectComparisonDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AccessibleChart(
                title: s.subjectComparisonChart,
                summary: s.subjectComparisonSummary,
                rows: _subjectRows(data),
                chart: SizedBox(
                  height: 200,
                  child: SimpleBarChart(
                    series: _subjectSeries(data),
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.subject,
                    primary: true,
                    cell: (context, row) => Text('${row['subjectName']}'),
                  ),
                  AppColumn(
                    label: s.averageScore,
                    numeric: true,
                    cell: (context, row) => Text('${row['average']}%'),
                  ),
                  AppColumn(
                    label: s.assessments,
                    numeric: true,
                    cell: (context, row) => Text('${row['assessments']}'),
                  ),
                  AppColumn(
                    label: s.studentsAssessed,
                    numeric: true,
                    cell: (context, row) => Text('${row['studentsAssessed']}'),
                  ),
                ],
                rows: (data['subjects'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['subjectId']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(subjectComparisonProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _subjectRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['subjects'] as List? ?? const [])) {
      rows.add(MapEntry('${row['subjectName']}', '${row['average']}%'));
    }
    return rows;
  }

  List<MapEntry<String, double>> _subjectSeries(Map<String, dynamic> data) {
    final List<MapEntry<String, double>> series = [];
    for (final row in (data['subjects'] as List? ?? const [])) {
      series.add(MapEntry('${row['subjectName']}', (row['average'] as num? ?? 0).toDouble()));
    }
    return series;
  }
}
