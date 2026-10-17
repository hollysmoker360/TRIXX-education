import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../notifications/presentation/notifications_provider.dart';

/// The parent/guardian family view: a child switcher plus attendance, latest
/// results, and announcements (specification section 6.2). Fee balance arrives
/// in R2.
class ParentFamilyScreen extends ConsumerStatefulWidget {
  const ParentFamilyScreen({super.key});

  @override
  ConsumerState<ParentFamilyScreen> createState() => _ParentFamilyScreenState();
}

class _ParentFamilyScreenState extends ConsumerState<ParentFamilyScreen> {
  String? _selectedChildId;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final children = ref.watch(_myChildrenProvider);

    return ScreenScaffold(
      title: s.family,
      description: s.students,
      embedded: true,
      child: AsyncView<List<Map<String, dynamic>>>(
        value: children,
        onRetry: () => ref.invalidate(_myChildrenProvider),
        isEmpty: (rows) => rows.isEmpty,
        emptyMessage: s.emptyList,
        builder: (rows) {
          final selected = _selectedChildId ?? '${rows.first['id']}';
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (rows.length > 1)
                Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.s16),
                  child: Wrap(
                    spacing: AppSpacing.s8,
                    children: [
                      for (final child in rows)
                        ChoiceChip(
                          avatar: Avatar(
                            name: '${child['firstName']} ${child['lastName']}',
                            size: 22,
                          ),
                          label: Text('${child['firstName']} ${child['lastName']}'),
                          selected: '${child['id']}' == selected,
                          onSelected: (_) => setState(() => _selectedChildId = '${child['id']}'),
                        ),
                    ],
                  ),
                ),
              _ChildSummary(childId: selected),
              const SizedBox(height: AppSpacing.s16),
              const _Announcements(),
            ],
          );
        },
      ),
    );
  }
}

class _ChildSummary extends ConsumerWidget {
  const _ChildSummary({required this.childId});

  final String childId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final summary = ref.watch(_attendanceSummaryProvider(childId));
    return SectionCard(
      title: s.attendance,
      subtitle: s.attendanceToday,
      child: AsyncView<Map<String, dynamic>>(
        value: summary,
        onRetry: () => ref.invalidate(_attendanceSummaryProvider(childId)),
        builder: (data) {
          final present = (data['present'] as num?)?.toInt() ?? 0;
          final absent = (data['absent'] as num?)?.toInt() ?? 0;
          final late = (data['late'] as num?)?.toInt() ?? 0;
          return Wrap(
            spacing: AppSpacing.s8,
            runSpacing: AppSpacing.s8,
            children: [
              StatusChip(label: '${s.markPresent} $present', kind: StatusKind.success, icon: Icons.check),
              StatusChip(label: '${s.markAbsent} $absent', kind: StatusKind.error, icon: Icons.close),
              StatusChip(label: '${s.markLate} $late', kind: StatusKind.warning, icon: Icons.schedule),
            ],
          );
        },
      ),
    );
  }
}

class _Announcements extends ConsumerWidget {
  const _Announcements();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final notifications = ref.watch(notificationsProvider);
    return SectionCard(
      title: s.notificationSettings,
      subtitle: s.notifyPreviewNote,
      action: AppButton(
        label: s.viewAll,
        variant: AppButtonVariant.text,
        onPressed: () => context.push(AppRoutes.notifications),
      ),
      child: AsyncView<List<Map<String, dynamic>>>(
        value: notifications,
        onRetry: () => ref.invalidate(notificationsProvider),
        isEmpty: (rows) => rows.isEmpty,
        emptyMessage: s.noNotifications,
        builder: (rows) => Column(
          children: [
            for (final item in rows.take(4))
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.campaign_outlined),
                title: Text('${item['title'] ?? ''}'),
                subtitle: Text('${item['body'] ?? ''}', maxLines: 2, overflow: TextOverflow.ellipsis),
              ),
          ],
        ),
      ),
    );
  }
}

final _myChildrenProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/people/me/children');
  return (data as List).cast<Map<String, dynamic>>();
});

final _attendanceSummaryProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/attendance/students/$studentId/summary');
  return data as Map<String, dynamic>;
});
