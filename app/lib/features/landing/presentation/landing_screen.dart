import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/brand/brand.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/layout/window_class.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/brand.dart';
import '../../../core/widgets/common.dart';

class LandingScreen extends ConsumerWidget {
  const LandingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final compact = windowClassOfContext(context).isCompact;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            const TrixxLogo(height: 32),
            const Spacer(),
            TextButton.icon(
              onPressed: () => ref.read(localeProvider.notifier).set(
                    Localizations.localeOf(context).languageCode == 'sw'
                        ? const Locale('en')
                        : const Locale('sw'),
                  ),
              icon: const Icon(Icons.language, size: 18),
              label: Text(
                Localizations.localeOf(context).languageCode == 'sw'
                    ? s.english
                    : s.kiswahili,
              ),
            ),
            const SizedBox(width: AppSpacing.s8),
            FilledButton.icon(
              onPressed: () => context.go(AppRoutes.login),
              icon: const Icon(Icons.login, size: 18),
              label: Text(s.signIn),
            ),
          ],
        ),
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            // Hero Section
            Container(
              width: double.infinity,
              padding: EdgeInsets.symmetric(
                vertical: compact ? AppSpacing.s32 : AppSpacing.s48,
                horizontal: AppSpacing.s24,
              ),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.6),
                    Theme.of(context).colorScheme.surface,
                  ],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 800),
                  child: Column(
                    children: [
                      TrixxLogo(height: compact ? 56 : 72),
                      const SizedBox(height: AppSpacing.s24),
                      Text(
                        'Next-Generation Multi-School Management & Learning Platform',
                        textAlign: TextAlign.center,
                        style: context.texts.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.s16),
                      Text(
                        'Streamline academics, CBE assessment, finance, parent communication, live e-learning, and school operations in one unified system.',
                        textAlign: TextAlign.center,
                        style: context.texts.bodyLarge?.copyWith(
                          color: context.tokens.textSecondary,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.s32),
                      Wrap(
                        spacing: AppSpacing.s16,
                        runSpacing: AppSpacing.s12,
                        alignment: WrapAlignment.center,
                        children: [
                          FilledButton.icon(
                            style: ElevatedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
                            ),
                            onPressed: () => context.go(AppRoutes.login),
                            icon: const Icon(Icons.arrow_forward),
                            label: const Text('Get Started / Sign In'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),

            // Key Modules Section
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.s24,
                vertical: AppSpacing.s32,
              ),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1000),
                child: Column(
                  children: [
                    Text(
                      'Comprehensive Platform Features',
                      textAlign: TextAlign.center,
                      style: context.texts.headlineSmall,
                    ),
                    const SizedBox(height: AppSpacing.s8),
                    Text(
                      'Everything your school needs to succeed',
                      textAlign: TextAlign.center,
                      style: context.texts.bodyMedium?.copyWith(
                        color: context.tokens.textSecondary,
                      ),
                    ),
                    const SizedBox(height: AppSpacing.s32),
                    GridView.count(
                      crossAxisCount: compact ? 1 : 3,
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      mainAxisSpacing: AppSpacing.s16,
                      crossAxisSpacing: AppSpacing.s16,
                      childAspectRatio: compact ? 2.5 : 1.3,
                      children: const [
                        _FeatureCard(
                          icon: Icons.school_outlined,
                          title: 'Academics & CBE',
                          description:
                              'Competency-based education, grading rubrics, automated report cards, and student progress tracking.',
                        ),
                        _FeatureCard(
                          icon: Icons.play_circle_outline,
                          title: 'E-Learning & Quizzes',
                          description:
                              'Digital courseware, live interactive lessons, auto-graded online quizzes, and rich learning resources.',
                        ),
                        _FeatureCard(
                          icon: Icons.family_restroom_outlined,
                          title: 'Parent Portal',
                          description:
                              'Real-time academic updates, attendance alerts, payment receipts, and direct messaging with educators.',
                        ),
                        _FeatureCard(
                          icon: Icons.account_balance_outlined,
                          title: 'Finance & Payments',
                          description:
                              'Fee structure management, mobile money and bank reconciliation, invoicing, and financial reporting.',
                        ),
                        _FeatureCard(
                          icon: Icons.groups_outlined,
                          title: 'HR & Operations',
                          description:
                              'Staff payroll, leave management, library catalogue, transport routes, canteen, and clinic records.',
                        ),
                        _FeatureCard(
                          icon: Icons.sync_outlined,
                          title: 'Offline First & Sync',
                          description:
                              'Keep working seamlessly without Internet connection and sync data automatically when back online.',
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            // Footer
            Container(
              padding: const EdgeInsets.all(AppSpacing.s24),
              color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
              child: Center(
                child: Column(
                  children: [
                    Text(
                      AppBrand.poweredBy,
                      style: context.texts.labelSmall,
                    ),
                    const SizedBox(height: AppSpacing.s8),
                    Text(
                      '© ${DateTime.now().year} Trixx Education. All rights reserved.',
                      style: context.texts.bodySmall?.copyWith(
                        color: context.tokens.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FeatureCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;

  const _FeatureCard({
    required this.icon,
    required this.title,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 32, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: AppSpacing.s12),
            Text(
              title,
              style: context.texts.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: AppSpacing.s4),
            Expanded(
              child: Text(
                description,
                style: context.texts.bodySmall?.copyWith(
                  color: context.tokens.textSecondary,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
