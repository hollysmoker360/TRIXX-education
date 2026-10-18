import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Attendance patterns report: daily attendance rates over a date range.
class AttendancePatternsScreen extends ConsumerStatefulWidget {
  const AttendancePatternsScreen({super.key});

  @override
  ConsumerState<AttendancePatternsScreen> createState() => _AttendancePatternsScreenState();
}

class _AttendancePatternsScreenState extends ConsumerState<AttendancePatternsScreen> {
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

    final report = ref.watch(attendancePatternsProvider(filters));

    return ScreenScaffold(
      title: s.attendancePatterns,
      description: s.attendancePatternsDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            _SummaryCard(
              label: s.overallAttendanceRate,
              value: '${data['overallRate'] ?? 0}%',
            ),
            const SizedBox(height: AppSpacing.s8),
            Expanded(
              child: AccessibleChart(
                title: s.attendancePatternsChart,
                summary: s.attendancePatternsSummary,
                rows: _patternRows(data),
                chart: SizedBox(
                  height: 200,
                  child: TrendSparkline(
                    values: _patternValues(data),
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
                    label: s.date,
                    primary: true,
                    cell: (context, row) => Text('${row['date']}'),
                  ),
                  AppColumn(
                    label: s.present,
                    numeric: true,
                    cell: (context, row) => Text('${row['present']}'),
                  ),
                  AppColumn(
                    label: s.absent,
                    numeric: true,
                    cell: (context, row) => Text('${row['absent']}'),
                  ),
                  AppColumn(
                    label: s.late,
                    numeric: true,
                    cell: (context, row) => Text('${row['late']}'),
                  ),
                  AppColumn(
                    label: s.excused,
                    numeric: true,
                    cell: (context, row) => Text('${row['excused']}'),
                  ),
                  AppColumn(
                    label: s.attendanceRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['attendanceRate']}%'),
                  ),
                ],
                rows: (data['daily'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['date']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(attendancePatternsProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _patternRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['daily'] as List? ?? const [])) {
      rows.add(MapEntry('${row['date']}', '${row['attendanceRate']}%'));
    }
    return rows;
  }

  List<double> _patternValues(Map<String, dynamic> data) {
    final values = <double>[];
    for (final row in (data['daily'] as List? ?? const [])) {
      values.add((row['attendanceRate'] as num? ?? 0).toDouble());
    }
    return values;
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
