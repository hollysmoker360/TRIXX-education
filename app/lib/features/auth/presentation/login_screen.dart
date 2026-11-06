import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/brand/brand.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/layout/window_class.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final ok = await ref.read(sessionProvider.notifier).signIn(_email.text.trim(), _password.text);
    if (!ok || !mounted) return;
    final session = ref.read(sessionProvider);
    if (session.user?.isPlatformAdmin == true) {
      context.go(AppRoutes.superAdmin);
    } else {
      context.go(AppRoutes.home);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final compact = windowClassOfContext(context).isCompact;

    return Scaffold(
      body: Stack(
        children: [
          // Background image with gradient overlay
          Positioned.fill(
            child: Image.asset(
              'assets/brand/trixx-app-icon-1024.png',
              fit: BoxFit.cover,
            ),
          ),
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    Theme.of(context).colorScheme.primary.withValues(alpha: 0.88),
                    Theme.of(context).colorScheme.surface.withValues(alpha: 0.94),
                  ],
                ),
              ),
            ),
          ),
          SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(AppSpacing.s24),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 440),
                  child: Card(
                    elevation: 6,
                    child: Padding(
                      padding: EdgeInsets.all(compact ? AppSpacing.s24 : AppSpacing.s32),
                      child: Form(
                        key: _formKey,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Center(
                              child: TrixxLogo(
                                height: compact ? 44 : 56,
                                markOnly: compact,
                              ),
                            ),
                            const SizedBox(height: AppSpacing.s16),
                            Semantics(
                              header: true,
                              child: Text(
                                s.appName,
                                textAlign: TextAlign.center,
                                style: context.texts.displaySmall,
                              ),
                            ),
                            const SizedBox(height: AppSpacing.s4),
                            Text(
                              s.tagline,
                              textAlign: TextAlign.center,
                              style: context.texts.bodyMedium?.copyWith(color: context.tokens.textSecondary),
                            ),
                            const SizedBox(height: AppSpacing.s24),
                            AppTextField(
                              controller: _email,
                              label: s.email,
                              required: true,
                              prefixIcon: Icons.alternate_email,
                              keyboardType: TextInputType.emailAddress,
                              autofillHints: const [AutofillHints.email],
                              textCapitalization: TextCapitalization.none,
                              onSubmitted: (_) => _submit(),
                              validator: (value) =>
                                  (value == null || value.trim().isEmpty) ? s.email : null,
                            ),
                            const SizedBox(height: AppSpacing.s16),
                            AppTextField(
                              controller: _password,
                              label: s.password,
                              required: true,
                              obscure: _obscure,
                              prefixIcon: Icons.lock_outline,
                              autofillHints: const [AutofillHints.password],
                              onSubmitted: (_) => _submit(),
                              validator: (value) => (value == null || value.isEmpty) ? s.password : null,
                              suffix: IconButton(
                                icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                                tooltip: _obscure ? s.showPassword : s.hidePassword,
                                onPressed: () => setState(() => _obscure = !_obscure),
                              ),
                            ),
                            Align(
                              alignment: Alignment.centerRight,
                              child: TextButton(
                                onPressed: () => context.push(AppRoutes.resetPassword),
                                child: Text(s.forgotPassword),
                              ),
                            ),
                            if (session.error != null) ...[
                              AppBanner(
                                kind: StatusKind.error,
                                message: session.error!,
                                onDismiss: () => ref.read(sessionProvider.notifier).clearError(),
                              ),
                              const SizedBox(height: AppSpacing.s16),
                            ],
                            AppButton(
                              label: s.signIn,
                              expand: true,
                              busy: session.signingIn,
                              onPressed: _submit,
                            ),
                            const SizedBox(height: AppSpacing.s12),
                            OutlinedButton.icon(
                              onPressed: () => context.go(AppRoutes.landing),
                              icon: const Icon(Icons.explore_outlined, size: 18),
                              label: const Text('About Platform & Features'),
                            ),
                            const SizedBox(height: AppSpacing.s16),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(Icons.language, size: 16, color: context.tokens.textSecondary),
                                const SizedBox(width: AppSpacing.s8),
                                TextButton(
                                  onPressed: () => ref.read(localeProvider.notifier).set(
                                        Localizations.localeOf(context).languageCode == 'sw'
                                            ? const Locale('en')
                                            : const Locale('sw'),
                                      ),
                                  child: Text(
                                    Localizations.localeOf(context).languageCode == 'sw'
                                        ? s.english
                                        : s.kiswahili,
                                  ),
                                ),
                              ],
                            ),
                            Text(
                              AppBrand.poweredBy,
                              textAlign: TextAlign.center,
                              style: context.texts.labelSmall,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
