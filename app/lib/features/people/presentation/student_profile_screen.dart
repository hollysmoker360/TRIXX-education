import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/page_header.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/layout/window_class.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// Student profile: identity, enrolment, guardians, attendance summary, and a
/// link to the report card. Sensitive identifiers are masked in list contexts
/// and shown only here with an explicit reveal (specification section 10).
class StudentProfileScreen extends ConsumerWidget {
  const StudentProfileScreen({super.key, required this.studentId, this.initialName});

  final String studentId;
  final String? initialName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    if (!session.can('people.view')) {
      return ScreenScaffold(title: initialName ?? s.students, child: const ForbiddenView());
    }

    final student = ref.watch(_studentProvider(studentId));
    final window = windowClassOfContext(context);

    return ScreenScaffold(
      title: initialName ?? s.students,
      breadcrumbs: [
        Crumb(s.people),
        Crumb(initialName ?? s.students),
      ],
      secondaryActions: [
        AppButton(
          label: s.reportCard,
          variant: AppButtonVariant.outline,
          icon: Icons.description_outlined,
          onPressed: () => context.push(
            AppRoutes.reportCardLocation(studentId),
            extra: initialName,
          ),
        ),
      ],
      child: AsyncView<Map<String, dynamic>>(
        value: student,
        onRetry: () => ref.invalidate(_studentProvider(studentId)),
        builder: (data) {
          final name = '${data['firstName'] ?? ''} ${data['lastName'] ?? ''}'.trim();
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.s16),
                  child: Row(
                    children: [
                      Avatar(name: name.isEmpty ? initialName ?? '?' : name, size: 56),
                      const SizedBox(width: AppSpacing.s16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(name, style: context.texts.titleLarge),
                            Text(
                              '${data['admissionNumber'] ?? data['studentNumber'] ?? ''}',
                              style: context.texts.bodySmall,
                            ),
                            const SizedBox(height: AppSpacing.s8),
                            Wrap(
                              spacing: AppSpacing.s8,
                              children: [
                                StatusChip(
                                  label: '${data['status'] ?? 'active'}',
                                  kind: '${data['status']}' == 'active'
                                      ? StatusKind.success
                                      : StatusKind.neutral,
                                  icon: Icons.verified_outlined,
                                ),
                                if (data['dateOfBirth'] != null)
                                  StatusChip(
                                    label: '${data['dateOfBirth']}',
                                    kind: StatusKind.neutral,
                                    icon: Icons.cake_outlined,
                                  ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.s16),
              if (window.contentColumns > 1)
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: _Details(student: data)),
                    const SizedBox(width: AppSpacing.s16),
                    Expanded(child: _Attendance(studentId: studentId)),
                  ],
                )
              else ...[
                _Details(student: data),
                const SizedBox(height: AppSpacing.s16),
                _Attendance(studentId: studentId),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _Details extends StatelessWidget {
  const _Details({required this.student});

  final Map<String, dynamic> student;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return SectionCard(
      title: s.details,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _row(context, s.email, '${student['email'] ?? '—'}'),
          _row(context, s.guardians, '${student['guardianName'] ?? '—'}'),
          _row(context, s.relation, '${student['guardianRelationship'] ?? '—'}'),
          _row(context, s.enrolment, '${student['classGroupName'] ?? student['classGroupId'] ?? '—'}'),
          _row(context, s.stream, '${student['streamName'] ?? student['streamId'] ?? '—'}'),
        ],
      ),
    );
  }

  Widget _row(BuildContext context, String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.s4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 120,
              child: Text(
                label,
                style: context.texts.labelMedium?.copyWith(color: context.tokens.textSecondary),
              ),
            ),
            Expanded(child: Text(value, style: context.texts.bodyMedium)),
          ],
        ),
      );
}

class _Attendance extends ConsumerWidget {
  const _Attendance({required this.studentId});

  final String studentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final summary = ref.watch(_attendanceSummaryProvider(studentId));
    return SectionCard(
      title: s.attendance,
      child: AsyncView<Map<String, dynamic>>(
        value: summary,
        onRetry: () => ref.invalidate(_attendanceSummaryProvider(studentId)),
        builder: (data) => Wrap(
          spacing: AppSpacing.s8,
          runSpacing: AppSpacing.s8,
          children: [
            StatusChip(
              label: '${s.markPresent} ${data['present'] ?? 0}',
              kind: StatusKind.success,
              icon: Icons.check,
            ),
            StatusChip(
              label: '${s.markAbsent} ${data['absent'] ?? 0}',
              kind: StatusKind.error,
              icon: Icons.close,
            ),
            StatusChip(
              label: '${s.markLate} ${data['late'] ?? 0}',
              kind: StatusKind.warning,
              icon: Icons.schedule,
            ),
          ],
        ),
      ),
    );
  }
}

final _studentProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/people/students/$studentId');
  return data as Map<String, dynamic>;
});

final _attendanceSummaryProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/attendance/students/$studentId/summary');
  return data as Map<String, dynamic>;
});
