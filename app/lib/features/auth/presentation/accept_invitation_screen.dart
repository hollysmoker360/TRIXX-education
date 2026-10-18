import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/networking/api_problem.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

class AcceptInvitationScreen extends ConsumerStatefulWidget {
  const AcceptInvitationScreen({super.key, this.token});

  final String? token;

  @override
  ConsumerState<AcceptInvitationScreen> createState() => _AcceptInvitationScreenState();
}

class _AcceptInvitationScreenState extends ConsumerState<AcceptInvitationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _displayName = TextEditingController();
  final _password = TextEditingController();
  final _confirmPassword = TextEditingController();
  final _phone = TextEditingController();

  bool _loading = true;
  bool _submitting = false;
  bool _completed = false;
  String? _error;

  Map<String, dynamic>? _details;

  @override
  void initState() {
    super.initState();
    _loadInvitation();
  }

  @override
  void dispose() {
    _displayName.dispose();
    _password.dispose();
    _confirmPassword.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _loadInvitation() async {
    final token = widget.token;
    if (token == null || token.trim().isEmpty) {
      setState(() {
        _loading = false;
        _error = 'Missing invitation token.';
      });
      return;
    }
    try {
      final api = ref.read(authApiClientProvider);
      final res = await api.get('/api/v1/invitations/${token.trim()}') as Map<String, dynamic>;
      setState(() {
        _details = res;
        _loading = false;
        if (res['email'] != null) {
          _displayName.text = res['email'].toString().split('@').first;
        }
      });
    } on ApiProblem catch (e) {
      setState(() {
        _loading = false;
        _error = e.detail;
      });
    } catch (e) {
      setState(() {
        _loading = false;
        _error = 'Failed to load invitation. Please verify your link.';
      });
    }
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    if (_password.text != _confirmPassword.text) {
      setState(() => _error = 'Passwords do not match.');
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
    });

    try {
      final api = ref.read(authApiClientProvider);
      await api.post('/api/v1/invitations/complete', data: {
        'token': widget.token?.trim(),
        'displayName': _displayName.text.trim(),
        'password': _password.text,
        'phone': _phone.text.trim().isEmpty ? null : _phone.text.trim(),
      });
      setState(() {
        _submitting = false;
        _completed = true;
      });
    } on ApiProblem catch (e) {
      setState(() {
        _submitting = false;
        _error = e.detail;
      });
    } catch (_) {
      setState(() {
        _submitting = false;
        _error = 'Failed to complete setup. Please try again.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.s24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.s24),
                  child: _buildContent(context),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    if (_loading) {
      return const Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TrixxLogo(height: 48),
          SizedBox(height: AppSpacing.s24),
          CircularProgressIndicator(),
          SizedBox(height: AppSpacing.s16),
          Text('Verifying invitation...'),
        ],
      );
    }

    if (_completed) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.check_circle_outline, size: 64, color: Colors.green),
          const SizedBox(height: AppSpacing.s16),
          Text('Setup Complete!', style: context.texts.titleLarge),
          const SizedBox(height: AppSpacing.s8),
          Text(
            'Your account setup is complete. You can now sign in with your email and password.',
            textAlign: TextAlign.center,
            style: context.texts.bodyMedium?.copyWith(color: context.tokens.textSecondary),
          ),
          const SizedBox(height: AppSpacing.s24),
          AppButton(
            label: 'Sign In Now',
            expand: true,
            onPressed: () => context.go(AppRoutes.login),
          ),
        ],
      );
    }

    if (_error != null && _details == null) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const TrixxLogo(height: 48),
          const SizedBox(height: AppSpacing.s16),
          AppBanner(
            kind: StatusKind.error,
            message: _error!,
          ),
          const SizedBox(height: AppSpacing.s24),
          AppButton(
            label: 'Go to Sign In',
            expand: true,
            onPressed: () => context.go(AppRoutes.login),
          ),
        ],
      );
    }

    final email = _details?['email'] ?? '';
    final role = _details?['roleCode'] ?? '';
    final schoolName = _details?['schoolName'];
    final studentName = _details?['studentName'];

    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Center(child: TrixxLogo(height: 48)),
          const SizedBox(height: AppSpacing.s16),
          Text(
            'Complete Account Setup',
            textAlign: TextAlign.center,
            style: context.texts.titleLarge,
          ),
          const SizedBox(height: AppSpacing.s8),
          Text(
            'Welcome! Set up your password to activate your account.',
            textAlign: TextAlign.center,
            style: context.texts.bodySmall?.copyWith(color: context.tokens.textSecondary),
          ),
          const SizedBox(height: AppSpacing.s16),
          Card(
            color: context.tokens.surfaceElevated,
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.s12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Email: $email', style: context.texts.labelMedium),
                  if (schoolName != null) ...[
                    const SizedBox(height: 4),
                    Text('School: $schoolName', style: context.texts.bodySmall),
                  ],
                  if (studentName != null) ...[
                    const SizedBox(height: 4),
                    Text('Linked Student: $studentName', style: context.texts.bodySmall?.copyWith(fontWeight: FontWeight.bold)),
                  ],
                  const SizedBox(height: 4),
                  Text('Role: $role', style: context.texts.bodySmall),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.s16),
          if (_error != null) ...[
            AppBanner(
              kind: StatusKind.error,
              message: _error!,
              onDismiss: () => setState(() => _error = null),
            ),
            const SizedBox(height: AppSpacing.s12),
          ],
          AppTextField(
            controller: _displayName,
            label: 'Full Name',
            required: true,
            prefixIcon: Icons.person_outline,
            validator: (v) => (v == null || v.trim().isEmpty) ? 'Full name is required' : null,
          ),
          const SizedBox(height: AppSpacing.s12),
          AppTextField(
            controller: _password,
            label: 'Set Password',
            required: true,
            obscure: true,
            prefixIcon: Icons.lock_outline,
            validator: (v) => (v == null || v.length < 8) ? 'Password must be at least 8 characters' : null,
          ),
          const SizedBox(height: AppSpacing.s12),
          AppTextField(
            controller: _confirmPassword,
            label: 'Confirm Password',
            required: true,
            obscure: true,
            prefixIcon: Icons.lock_outline,
            validator: (v) => (v == null || v.isEmpty) ? 'Confirm password is required' : null,
          ),
          const SizedBox(height: AppSpacing.s12),
          AppTextField(
            controller: _phone,
            label: 'Phone Number (Optional)',
            prefixIcon: Icons.phone_outlined,
            keyboardType: TextInputType.phone,
          ),
          const SizedBox(height: AppSpacing.s24),
          AppButton(
            label: 'Complete Setup & Activate',
            expand: true,
            busy: _submitting,
            onPressed: _submit,
          ),
        ],
      ),
    );
  }
}
