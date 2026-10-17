import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/brand/brand.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// School picker shown when a user belongs to more than one school, and when the
/// stored selection is no longer valid (specification section 6.1). Selecting a
/// school clears school-scoped caches by re-keying the shell.
class SchoolSelectScreen extends ConsumerWidget {
  const SchoolSelectScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final memberships = session.memberships.where((m) => m.status == 'active').toList();

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.s24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 520),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: TrixxLogo(height: 48)),
                  const SizedBox(height: AppSpacing.s24),
                  Text(
                    s.selectSchoolTitle,
                    textAlign: TextAlign.center,
                    style: context.texts.headlineSmall ?? context.texts.titleLarge,
                  ),
                  const SizedBox(height: AppSpacing.s16),
                  if (memberships.isEmpty)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(AppSpacing.s16),
                        child: Column(
                          children: [
                            Icon(Icons.info_outline, size: 36, color: context.tokens.textSecondary),
                            const SizedBox(height: AppSpacing.s8),
                            Text(s.notAMember, textAlign: TextAlign.center),
                          ],
                        ),
                      ),
                    )
                  else
                    Card(
                      child: Column(
                        children: [
                          for (final membership in memberships)
                            ListTile(
                              leading: Avatar(name: membership.schoolName, size: 40, square: true),
                              title: Text(membership.schoolName),
                              subtitle: Text(
                                [
                                  if (membership.title != null) membership.title!,
                                  if (membership.roles.isNotEmpty) membership.roles.join(', '),
                                ].join(' · '),
                              ),
                              trailing: const Icon(Icons.arrow_forward_ios, size: 14),
                              onTap: () async {
                                await ref
                                    .read(sessionProvider.notifier)
                                    .selectSchool(membership.schoolId);
                                if (context.mounted) context.go(AppRoutes.home);
                              },
                            ),
                        ],
                      ),
                    ),
                  const SizedBox(height: AppSpacing.s16),
                  OutlinedButton.icon(
                    onPressed: () => ref.read(sessionProvider.notifier).signOut(),
                    icon: const Icon(Icons.logout, size: 18),
                    label: Text(s.signOut),
                  ),
                  const SizedBox(height: AppSpacing.s16),
                  Text(
                    AppBrand.tagline,
                    textAlign: TextAlign.center,
                    style: context.texts.labelSmall,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
