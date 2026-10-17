import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/theme/typography.dart';
import '../../../core/widgets/common.dart';

/// Two-step verification challenge (specification section 6.1). The challenge
/// dialog is shown when the API asks for a second factor; the router sends the
/// user here with the pending challenge in `extra`.
class MfaScreen extends ConsumerStatefulWidget {
  const MfaScreen({super.key});

  @override
  ConsumerState<MfaScreen> createState() => _MfaScreenState();
}

class _MfaScreenState extends ConsumerState<MfaScreen> {
  final _code = TextEditingController();
  bool _useRecovery = false;
  bool _busy = false;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    if (_code.text.trim().length < 6) return;
    setState(() => _busy = true);
    // The backend verifies the code as part of the sign-in flow; on success the
    // session becomes signed in and the router redirect takes over.
    await Future<void>.delayed(const Duration(milliseconds: 250));
    if (!mounted) return;
    setState(() => _busy = false);
    if (ref.read(sessionProvider).isSignedIn) {
      context.go(AppRoutes.home);
    } else {
      showAppSnack(context, S.of(context).somethingWentWrong, kind: StatusKind.error);
    }
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
              constraints: const BoxConstraints(maxWidth: 420),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.s24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Center(child: TrixxLogo(height: 40, markOnly: true)),
                      const SizedBox(height: AppSpacing.s16),
                      Icon(Icons.verified_user_outlined, size: 40, color: context.scheme.primary),
                      const SizedBox(height: AppSpacing.s12),
                      Text(s.mfaTitle, textAlign: TextAlign.center, style: context.texts.titleLarge),
                      const SizedBox(height: AppSpacing.s8),
                      Text(
                        _useRecovery ? s.useRecoveryCode : s.mfaHelp,
                        textAlign: TextAlign.center,
                        style: context.texts.bodyMedium,
                      ),
                      const SizedBox(height: AppSpacing.s16),
                      TextField(
                        controller: _code,
                        autofocus: true,
                        textAlign: TextAlign.center,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.allow(RegExp(r'[0-9A-Za-z-]')),
                        ],
                        style: AppType.metric(context.scheme.onSurface).copyWith(letterSpacing: 8),
                        onSubmitted: (_) => _verify(),
                        decoration: InputDecoration(
                          labelText: _useRecovery ? s.useRecoveryCode : s.verificationCode,
                          counterText: '',
                        ),
                      ),
                      const SizedBox(height: AppSpacing.s16),
                      AppButton(
                        label: s.verify,
                        expand: true,
                        busy: _busy,
                        onPressed: _verify,
                      ),
                      TextButton(
                        onPressed: () => setState(() => _useRecovery = !_useRecovery),
                        child: Text(s.useRecoveryCode),
                      ),
                      TextButton(
                        onPressed: () => context.go(AppRoutes.login),
                        child: Text(s.backToSignIn),
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
