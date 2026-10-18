import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Student engagement report: per-student assignment submission rate and attendance rate.
class StudentEngagementScreen extends ConsumerStatefulWidget {
  const StudentEngagementScreen({super.key});

  @override
  ConsumerState<StudentEngagementScreen> createState() => _StudentEngagementScreenState();
}

class _StudentEngagementScreenState extends ConsumerState<StudentEngagementScreen> {
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

    final report = ref.watch(engagementProvider(filters));

    return ScreenScaffold(
      title: s.studentEngagement,
      description: s.studentEngagementDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
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
                    label: s.submissionRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['submissionRate']}%'),
                  ),
                  AppColumn(
                    label: s.assignmentsSubmitted,
                    numeric: true,
                    cell: (context, row) => Text('${row['assignmentsSubmitted']}'),
                  ),
                  AppColumn(
                    label: s.assignmentsMissing,
                    numeric: true,
                    cell: (context, row) => Text('${row['assignmentsMissing']}'),
                  ),
                  AppColumn(
                    label: s.attendanceRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['attendanceRate']}%'),
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
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(engagementProvider(filters))),
      ),
    );
  }
}
