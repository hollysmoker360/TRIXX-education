import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Term over term report: class average and pass rate trend across terms.
class TermOverTermScreen extends ConsumerStatefulWidget {
  const TermOverTermScreen({super.key});

  @override
  ConsumerState<TermOverTermScreen> createState() => _TermOverTermScreenState();
}

class _TermOverTermScreenState extends ConsumerState<TermOverTermScreen> {
  String? _classGroupId;
  String? _subjectId;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_classGroupId != null) filters['classGroupId'] = _classGroupId;
    if (_subjectId != null) filters['subjectId'] = _subjectId;

    final report = ref.watch(termOverTermProvider(filters));

    return ScreenScaffold(
      title: s.termOverTerm,
      description: s.termOverTermDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AccessibleChart(
                title: s.termOverTermChart,
                summary: s.termOverTermSummary,
                rows: _termRows(data),
                chart: SizedBox(
                  height: 200,
                  child: TrendSparkline(
                    values: _termValues(data),
                    color: context.scheme.primary,
                    fill: true,
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.term,
                    primary: true,
                    cell: (context, row) => Text('${row['term']}'),
                  ),
                  AppColumn(
                    label: s.averageScore,
                    numeric: true,
                    cell: (context, row) => Text('${row['average']}%'),
                  ),
                  AppColumn(
                    label: s.passRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['passRate']}%'),
                  ),
                  AppColumn(
                    label: s.studentsAssessed,
                    numeric: true,
                    cell: (context, row) => Text('${row['studentsAssessed']}'),
                  ),
                ],
                rows: (data['terms'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['term']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(termOverTermProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _termRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['terms'] as List? ?? const [])) {
      rows.add(MapEntry('${row['term']}', '${row['average']}%'));
    }
    return rows;
  }

  List<double> _termValues(Map<String, dynamic> data) {
    final values = <double>[];
    for (final row in (data['terms'] as List? ?? const [])) {
      values.add((row['average'] as num? ?? 0).toDouble());
    }
    return values;
  }
}
