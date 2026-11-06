import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import 'student_success_providers.dart';

/// Student Success dashboard — at-risk students, open referrals, active plans.
class StudentSuccessDashboardScreen extends ConsumerWidget {
  const StudentSuccessDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final canIntervene = session.can('student_success.intervene');

    return ScreenScaffold(
      title: s.studentSuccess,
      description: s.studentSuccessDescription,
      embedded: true,
      primaryAction: canIntervene
          ? AppButton(
              label: s.createReferral,
              variant: AppButtonVariant.primary,
              icon: Icons.add,
              onPressed: () => context.push(AppRoutes.studentReferral),
            )
          : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _StatCard(
            label: s.studentsAtRisk,
            value: '12',
            subtitle: s.needsReview,
            kind: StatusKind.warning,
          ),
          _StatCard(
            label: s.openReferrals,
            value: '3',
            subtitle: s.pendingAction,
            kind: StatusKind.info,
          ),
          _StatCard(
            label: s.activeInterventions,
            value: '7',
            subtitle: s.onTrack,
            kind: StatusKind.success,
          ),
          const SizedBox(height: AppSpacing.s16),
          Text(s.recentActivity, style: context.texts.titleMedium),
          const SizedBox(height: AppSpacing.s8),
          Expanded(
            child: _TimelineList(),
          ),
        ],
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({
    required this.label,
    required this.value,
    required this.subtitle,
    required this.kind,
  });

  final String label;
  final String value;
  final String subtitle;
  final StatusKind kind;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(AppSpacing.s8),
              decoration: BoxDecoration(
                color: context.scheme.status(kind)
                    .withValues(alpha: 0.10),
                borderRadius: AppRadius.mdAll,
              ),
              child: Icon(Icons.insights_outlined,
                  color: context.scheme.status(kind), size: 20),
            ),
            const SizedBox(width: AppSpacing.s12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(value,
                      style: context.texts.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                  Text(label, style: context.texts.bodyMedium),
                  Text(subtitle,
                      style: context.texts.bodySmall?.copyWith(
                        color: context.tokens.textSecondary,
                      )),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TimelineList extends ConsumerWidget {
  const _TimelineList();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return const ListTile(
      leading: Icon(Icons.history_outlined),
      title: Text('No recent activity'),
      subtitle: Text('Create a referral or intervention plan to get started.'),
    );
  }
}
