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

/// A single student's success profile: timeline, indicators, referrals, plans.
class StudentProfileScreen extends ConsumerStatefulWidget {
  const StudentProfileScreen({super.key, required this.studentId});

  final String studentId;

  @override
  ConsumerState<StudentProfileScreen> createState() => _StudentProfileScreenState();
}

class _StudentProfileScreenState extends ConsumerState<StudentProfileScreen> {
  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final canRefer = session.can('student_success.refer');
    final canIntervene = session.can('student_success.intervene');

    final profile = ref.watch(studentProfileProvider(widget.studentId));
    final indicators = ref.watch(riskIndicatorsProvider(widget.studentId));
    final referrals = ref.watch(supportReferralsProvider(widget.studentId));
    final plans = ref.watch(interventionPlansProvider(widget.studentId));
    final timeline = ref.watch(studentTimelineProvider(widget.studentId));

    return ScreenScaffold(
      title: s.studentProfile,
      description: s.studentSuccessDescription,
      embedded: true,
      primaryAction: canRefer
          ? AppButton(
              label: s.createReferral,
              variant: AppButtonVariant.primary,
              icon: Icons.add,
              onPressed: () => context.push(AppRoutes.studentReferral,
                  extra: widget.studentId),
            )
          : null,
      child: profile.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _RiskHeader(
              riskLevel: data['overallRiskLevel'] ?? 'low',
              name: data['studentName'] ?? 'Student',
            ),
            const SizedBox(height: AppSpacing.s16),
            Row(
              children: [
                Expanded(child: _StatTile(s.riskLevel, data['overallRiskLevel'] ?? '—')),
                Expanded(child: _StatTile(s.attendanceConcern,
                    data['attendanceConcern'] == true ? s.yes : s.no)),
                Expanded(child: _StatTile(s.academicDecline,
                    data['academicDecline'] == true ? s.yes : s.no)),
                Expanded(child: _StatTile(s.incompleteWork,
                    data['incompleteWork'] == true ? s.yes : s.no)),
              ],
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: RefreshIndicator(
                onRefresh: () async {
                  ref.invalidate(studentProfileProvider(widget.studentId));
                  ref.invalidate(riskIndicatorsProvider(widget.studentId));
                  ref.invalidate(supportReferralsProvider(widget.studentId));
                  ref.invalidate(interventionPlansProvider(widget.studentId));
                  ref.invalidate(studentTimelineProvider(widget.studentId));
                },
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (indicators.value != null && indicators.value!.isNotEmpty) ...[
                        Text(s.riskIndicators, style: context.texts.titleMedium),
                        const SizedBox(height: AppSpacing.s8),
                        ...indicators.value!.map((ind) => _IndicatorTile(ind)).toList(),
                        const SizedBox(height: AppSpacing.s16),
                      ],
                      if (referrals.value != null && referrals.value!.isNotEmpty) ...[
                        Text(s.supportReferrals, style: context.texts.titleMedium),
                        const SizedBox(height: AppSpacing.s8),
                        ...referrals.value!.map((r) => _ReferralTile(r)).toList(),
                        const SizedBox(height: AppSpacing.s16),
                      ],
                      if (plans.value != null && plans.value!.isNotEmpty) ...[
                        Text(s.interventionPlans, style: context.texts.titleMedium),
                        const SizedBox(height: AppSpacing.s8),
                        ...plans.value!.map((p) => _PlanTile(p, canIntervene)).toList(),
                        const SizedBox(height: AppSpacing.s16),
                      ],
                      if (timeline.value != null && timeline.value!.isNotEmpty) ...[
                        Text(s.timeline, style: context.texts.titleMedium),
                        const SizedBox(height: AppSpacing.s8),
                        ...timeline.value!.map((e) => _TimelineTile(e)).toList(),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(studentProfileProvider(widget.studentId))),
      ),
    );
  }
}

class _RiskHeader extends StatelessWidget {
  const _RiskHeader({required this.riskLevel, required this.name});

  final String riskLevel;
  final String name;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final kind = switch (riskLevel) {
      'critical' => StatusKind.error,
      'high' => StatusKind.warning,
      'moderate' => StatusKind.info,
      _ => StatusKind.neutral,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s12, vertical: AppSpacing.s12),
              decoration: BoxDecoration(
                color: context.scheme.status(kind).withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(riskLevel.toUpperCase(),
                  style: context.texts.labelSmall?.copyWith(
                    color: context.scheme.status(kind),
                    fontWeight: FontWeight.w700,
                  )),
            ),
            const SizedBox(width: AppSpacing.s12),
            Expanded(child: Text(name, style: context.texts.titleLarge)),
          ],
        ),
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(value,
                style: context.texts.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
            Text(label, style: context.texts.bodySmall?.copyWith(color: context.tokens.textSecondary)),
          ],
        ),
      ),
    );
  }
}

class _IndicatorTile extends StatelessWidget {
  const _IndicatorTile(this.indicator);

  final Map<String, dynamic> indicator;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(Icons.warning_amber_outlined,
          color: context.scheme.status(StatusKind.warning)),
      title: Text('${indicator['indicatorType'] ?? indicator['description']}'),
      subtitle: Text('${indicator['severity'] ?? ''} — ${indicator['description'] ?? ''}'),
      trailing: indicator['acknowledged'] == true
          ? const Icon(Icons.check_circle, color: Colors.green)
          : const Icon(Icons.circle_outlined, color: Colors.grey),
    );
  }
}

class _ReferralTile extends StatelessWidget {
  const _ReferralTile(this.referral);

  final Map<String, dynamic> referral;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return ListTile(
      leading: const Icon(Icons.person_add_outlined),
      title: Text('${referral['referralType'] ?? s.referral}'),
      subtitle: Text('${referral['priority'] ?? ''} • ${referral['status'] ?? ''}'),
      trailing: Text('${referral['reason'] ?? ''}',
          maxLines: 1, overflow: TextOverflow.ellipsis),
    );
  }
}

class _PlanTile extends StatelessWidget {
  const _PlanTile(this.plan, this.canIntervene);

  final Map<String, dynamic> plan;
  final bool canIntervene;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Card(
      child: ListTile(
        title: Text('${plan['title'] ?? s.interventionPlan}'),
        subtitle: Text('${plan['status'] ?? ''} • ${plan['reviewDate'] ?? ''}'),
        trailing: canIntervene
            ? IconButton(
                icon: const Icon(Icons.edit_outlined),
                onPressed: () {},
              )
            : null,
      ),
    );
  }
}

class _TimelineTile extends StatelessWidget {
  const _TimelineTile(this.event);

  final Map<String, dynamic> event;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return ListTile(
      leading: const Icon(Icons.access_time_outlined),
      title: Text('${event['eventType'] ?? s.event}'),
      subtitle: Text('${event['title'] ?? ''}'),
      trailing: Text('${event['occurredAt'] ?? ''}',
          style: context.texts.bodySmall?.copyWith(color: context.tokens.textSecondary)),
    );
  }
}
