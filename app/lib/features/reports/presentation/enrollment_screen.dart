import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Enrollment report: enrollment counts and rates by grade level.
class EnrollmentScreen extends ConsumerStatefulWidget {
  const EnrollmentScreen({super.key});

  @override
  ConsumerState<EnrollmentScreen> createState() => _EnrollmentScreenState();
}

class _EnrollmentScreenState extends ConsumerState<EnrollmentScreen> {
  String? _asOf;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_asOf != null) filters['asOf'] = _asOf;

    final report = ref.watch(enrollmentProvider(filters));

    return ScreenScaffold(
      title: s.enrollment,
      description: s.enrollmentDescription,
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
                    label: s.grade,
                    primary: true,
                    cell: (context, row) => Text('${row['grade']}'),
                  ),
                  AppColumn(
                    label: s.totalEnrolled,
                    numeric: true,
                    cell: (context, row) => Text('${row['totalEnrolled']}'),
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
                    label: s.enrollmentRate,
                    numeric: true,
                    cell: (context, row) => Text('${row['enrollmentRate']}%'),
                  ),
                ],
                rows: (data['grades'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['grade']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(enrollmentProvider(filters))),
      ),
    );
  }
}
