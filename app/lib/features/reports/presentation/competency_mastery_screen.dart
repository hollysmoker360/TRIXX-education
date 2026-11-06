import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Competency mastery report: how many students mastered / developing / beginning.
class CompetencyMasteryScreen extends ConsumerStatefulWidget {
  const CompetencyMasteryScreen({super.key});

  @override
  ConsumerState<CompetencyMasteryScreen> createState() => _CompetencyMasteryScreenState();
}

class _CompetencyMasteryScreenState extends ConsumerState<CompetencyMasteryScreen> {
  String? _classGroupId;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_classGroupId != null) filters['classGroupId'] = _classGroupId;

    final report = ref.watch(competencyMasteryProvider(filters));

    return ScreenScaffold(
      title: s.competencyMastery,
      description: s.competencyMasteryDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AccessibleChart(
                title: s.competencyMasteryChart,
                summary: s.competencyMasterySummary,
                rows: _competencyRows(data),
                chart: SizedBox(
                  height: 200,
                  child: SimpleBarChart(
                    series: _competencySeries(data),
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.competency,
                    primary: true,
                    cell: (context, row) => Text('${row['competencyName']}'),
                  ),
                  AppColumn(
                    label: s.code,
                    cell: (context, row) => Text('${row['competencyId']}'),
                  ),
                  AppColumn(
                    label: s.mastered,
                    numeric: true,
                    cell: (context, row) => Text('${row['mastered']}'),
                  ),
                  AppColumn(
                    label: s.developing,
                    numeric: true,
                    cell: (context, row) => Text('${row['developing']}'),
                  ),
                  AppColumn(
                    label: s.beginning,
                    numeric: true,
                    cell: (context, row) => Text('${row['beginning']}'),
                  ),
                  AppColumn(
                    label: s.masteryRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['masteryPercent']}%'),
                  ),
                ],
                rows: (data['competencies'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['competencyId']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(competencyMasteryProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _competencyRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['competencies'] as List? ?? const [])) {
      rows.add(MapEntry('${row['competencyName']}', '${row['masteryPercent']}%'));
    }
    return rows;
  }

  List<MapEntry<String, double>> _competencySeries(Map<String, dynamic> data) {
    final List<MapEntry<String, double>> series = [];
    for (final row in (data['competencies'] as List? ?? const [])) {
      series.add(MapEntry('${row['competencyName']}', (row['masteryPercent'] as num? ?? 0).toDouble()));
    }
    return series;
  }
}
