import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/brand/brand.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// Password recovery (specification section 6.1). Always shows the same
/// confirmation so the screen cannot be used to discover which emails exist.
class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({super.key});

  @override
  ConsumerState<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _submitted = false;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.s24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.s24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Center(child: TrixxLogo(height: 40, markOnly: true)),
                      const SizedBox(height: AppSpacing.s16),
                      if (_submitted) ...[
                        Icon(Icons.mark_email_read_outlined, size: 40, color: context.tokens.success),
                        const SizedBox(height: AppSpacing.s12),
                        Text(
                          s.checkYourEmail,
                          textAlign: TextAlign.center,
                          style: context.texts.titleLarge,
                        ),
                        const SizedBox(height: AppSpacing.s8),
                        Text(
                          s.resetPasswordHelp,
                          textAlign: TextAlign.center,
                          style: context.texts.bodyMedium,
                        ),
                        const SizedBox(height: AppSpacing.s16),
                        AppButton(
                          label: s.backToSignIn,
                          expand: true,
                          variant: AppButtonVariant.outline,
                          onPressed: () => context.go(AppRoutes.login),
                        ),
                      ] else ...[
                        Text(s.resetPassword, style: context.texts.titleLarge),
                        const SizedBox(height: AppSpacing.s8),
                        Text(
                          s.resetPasswordHelp,
                          style: context.texts.bodyMedium?.copyWith(
                            color: context.tokens.textSecondary,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.s16),
                        Form(
                          key: _formKey,
                          child: AppTextField(
                            controller: _email,
                            label: s.email,
                            required: true,
                            prefixIcon: Icons.alternate_email,
                            keyboardType: TextInputType.emailAddress,
                            validator: (value) =>
                                (value == null || value.trim().isEmpty) ? s.email : null,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.s16),
                        AppButton(
                          label: s.sendResetLink,
                          expand: true,
                          onPressed: () {
                            if (_formKey.currentState?.validate() ?? false) {
                              setState(() => _submitted = true);
                            }
                          },
                        ),
                        TextButton(
                          onPressed: () => context.go(AppRoutes.login),
                          child: Text(s.backToSignIn),
                        ),
                      ],
                      const SizedBox(height: AppSpacing.s8),
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
        ),
      ),
    );
  }
}
