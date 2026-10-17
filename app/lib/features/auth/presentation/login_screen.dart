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
  bool _showDemo = false;

  static const _demoAccounts = [
    ('principal@kilimani.ac.ke', 'Principal — Grace'),
    ('teacher.david@kilimani.ac.ke', 'Teacher — David'),
    ('student.faith@kilimani.ac.ke', 'Student — Faith'),
    ('parent.alice@kilimani.ac.ke', 'Parent — Alice'),
    ('admin@kilimani.ac.ke', 'Admin — Peter'),
  ];

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final ok = await ref.read(sessionProvider.notifier).signIn(_email.text.trim(), _password.text);
    if (ok && mounted) context.go(AppRoutes.home);
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final compact = windowClassOfContext(context).isCompact;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.s24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Card(
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
                        const SizedBox(height: AppSpacing.s8),
                        TextButton.icon(
                          onPressed: () => setState(() => _showDemo = !_showDemo),
                          icon: const Icon(Icons.science_outlined, size: 18),
                          label: Text(s.demoAccounts),
                        ),
                        if (_showDemo)
                          Card(
                            child: Column(
                              children: [
                                for (final (email, label) in _demoAccounts)
                                  ListTile(
                                    dense: true,
                                    title: Text(label),
                                    subtitle: Text(email),
                                    trailing: const Icon(Icons.arrow_forward, size: 16),
                                    onTap: () {
                                      _email.text = email;
                                      _password.text = 'Demo1234!';
                                      _submit();
                                    },
                                  ),
                                Padding(
                                  padding: const EdgeInsets.fromLTRB(
                                    AppSpacing.s16,
                                    0,
                                    AppSpacing.s16,
                                    AppSpacing.s12,
                                  ),
                                  child: Text(s.demoPasswordHint, style: context.texts.bodySmall),
                                ),
                              ],
                            ),
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
    );
  }
}
