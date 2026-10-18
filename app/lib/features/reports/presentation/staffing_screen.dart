import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Staffing report: staff counts by role and status.
class StaffingScreen extends ConsumerWidget {
  const StaffingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);

    final report = ref.watch(staffingProvider);

    return ScreenScaffold(
      title: s.staffing,
      description: s.staffingDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AccessibleChart(
                title: s.staffingChart,
                summary: s.staffingSummary,
                rows: _staffingRows(data),
                chart: SizedBox(
                  height: 200,
                  child: SimpleBarChart(
                    series: _staffingSeries(data),
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.role,
                    primary: true,
                    cell: (context, row) => Text('${row['role']}'),
                  ),
                  AppColumn(
                    label: s.totalStaff,
                    numeric: true,
                    cell: (context, row) => Text('${row['totalStaff']}'),
                  ),
                  AppColumn(
                    label: s.active,
                    numeric: true,
                    cell: (context, row) => Text('${row['active']}'),
                  ),
                  AppColumn(
                    label: s.onLeave,
                    numeric: true,
                    cell: (context, row) => Text('${row['onLeave']}'),
                  ),
                ],
                rows: (data['roles'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['role']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(staffingProvider)),
      ),
    );
  }

  List<MapEntry<String, String>> _staffingRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['roles'] as List? ?? const [])) {
      rows.add(MapEntry('${row['role']}', '${row['totalStaff']}'));
    }
    return rows;
  }

  List<MapEntry<String, double>> _staffingSeries(Map<String, dynamic> data) {
    final List<MapEntry<String, double>> series = [];
    for (final row in (data['roles'] as List? ?? const [])) {
      series.add(MapEntry('${row['role']}', (row['totalStaff'] as num? ?? 0).toDouble()));
    }
    return series;
  }
}
