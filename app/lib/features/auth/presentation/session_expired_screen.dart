import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// Shown when a refresh token is rejected or the session times out
/// (specification section 10). Explains the state plainly and offers a single
/// action; no data remains on screen.
class SessionExpiredScreen extends ConsumerWidget {
  const SessionExpiredScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.s24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.s24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const TrixxLogo(height: 40, markOnly: true),
                      const SizedBox(height: AppSpacing.s16),
                      Icon(Icons.lock_clock_outlined, size: 40, color: context.tokens.warning),
                      const SizedBox(height: AppSpacing.s12),
                      Text(s.sessionExpired, style: context.texts.titleLarge),
                      const SizedBox(height: AppSpacing.s8),
                      Text(
                        s.sessionExpiredHelp,
                        textAlign: TextAlign.center,
                        style: context.texts.bodyMedium,
                      ),
                      const SizedBox(height: AppSpacing.s16),
                      AppButton(
                        label: s.signIn,
                        expand: true,
                        onPressed: () => context.go(AppRoutes.login),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
