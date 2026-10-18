import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/layout/page_header.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

class SuperAdminScreen extends ConsumerStatefulWidget {
  const SuperAdminScreen({super.key});

  @override
  ConsumerState<SuperAdminScreen> createState() => _SuperAdminScreenState();
}

class _SuperAdminScreenState extends ConsumerState<SuperAdminScreen> {
  bool _loading = true;
  String? _error;
  Map<String, dynamic>? _dashboard;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _fetchDashboard();
  }

  Future<void> _fetchDashboard() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = ref.read(apiClientProvider);
      final res = await api.get('/api/v1/super-admin/dashboard') as Map<String, dynamic>;
      setState(() {
        _dashboard = res;
        _loading = false;
      });
    } on ApiProblem catch (e) {
      setState(() {
        _error = e.detail;
        _loading = false;
      });
    } catch (_) {
      setState(() {
        _error = 'Failed to load platform dashboard metrics.';
        _loading = false;
      });
    }
  }

  void _showInviteModal() {
    showDialog(
      context: context,
      builder: (ctx) => _InviteSchoolModal(
        onSuccess: (result) {
          _fetchDashboard();
          _showSetupLinkDialog(result);
        },
      ),
    );
  }

  void _showSetupLinkDialog(Map<String, dynamic> result) {
    final setupUrl = result['setupUrl'] ?? '';
    final token = result['invitationToken'] ?? '';
    final schoolName = result['schoolName'] ?? '';
    final email = result['adminEmail'] ?? '';

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('School Invitation Sent for $schoolName'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('An invitation token was generated for $email:'),
            const SizedBox(height: AppSpacing.s12),
            SelectableText(
              'Invitation Token:\n$token',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: AppSpacing.s12),
            SelectableText(
              'Setup Link:\n$setupUrl',
              style: const TextStyle(color: Colors.blue),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: setupUrl.toString()));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Setup link copied to clipboard!')),
              );
            },
            child: const Text('Copy Setup Link'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Future<void> _toggleSchoolStatus(Map<String, dynamic> school) async {
    final id = school['id'];
    final currentStatus = school['status']?.toString() ?? 'active';
    final targetStatus = currentStatus == 'active' ? 'suspended' : 'active';

    final reasonController = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${targetStatus == 'suspended' ? 'Suspend' : 'Activate'} ${school['name']}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Are you sure you want to change status to $targetStatus?'),
            const SizedBox(height: AppSpacing.s12),
            AppTextField(
              controller: reasonController,
              label: 'Reason',
              required: true,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(targetStatus == 'suspended' ? 'Suspend' : 'Activate'),
          ),
        ],
      ),
    );

    if (confirmed == true && reasonController.text.trim().isNotEmpty) {
      try {
        final api = ref.read(apiClientProvider);
        await api.put('/api/v1/super-admin/schools/$id/status', data: {
          'status': targetStatus,
          'reason': reasonController.text.trim(),
        });
        _fetchDashboard();
      } on ApiProblem catch (e) {
        if (mounted) showAppSnack(context, e.detail, kind: StatusKind.error);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const ScreenScaffold(
        title: 'Platform Super Admin',
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (_error != null) {
      return ScreenScaffold(
        title: 'Platform Super Admin',
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppBanner(kind: StatusKind.error, message: _error!),
              const SizedBox(height: AppSpacing.s16),
              AppButton(label: 'Retry', onPressed: _fetchDashboard),
            ],
          ),
        ),
      );
    }

    final schools = (_dashboard?['schools'] as List? ?? [])
        .cast<Map<String, dynamic>>()
        .where((s) => _query.isEmpty || s['name'].toString().toLowerCase().contains(_query.toLowerCase()) || s['slug'].toString().toLowerCase().contains(_query.toLowerCase()))
        .toList();

    return ScreenScaffold(
      title: 'Platform Super Admin',
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(AppSpacing.s16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            PageHeader(
              title: 'Platform Super Admin Dashboard',
              description: 'Global school tenant registration, platform health, and security administration',
              primaryAction: AppButton(
                label: 'Register & Invite School',
                icon: Icons.add,
                onPressed: _showInviteModal,
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'Total Schools',
                    value: '${_dashboard?['totalSchools'] ?? 0}',
                    status: StatusKind.neutral,
                    icon: Icons.school_outlined,
                  ),
                ),
                const SizedBox(width: AppSpacing.s12),
                Expanded(
                  child: MetricCard(
                    label: 'Active Tenants',
                    value: '${_dashboard?['activeTenants'] ?? 0}',
                    status: StatusKind.success,
                    icon: Icons.check_circle_outline,
                  ),
                ),
                const SizedBox(width: AppSpacing.s12),
                Expanded(
                  child: MetricCard(
                    label: 'Suspended',
                    value: '${_dashboard?['suspendedTenants'] ?? 0}',
                    status: StatusKind.warning,
                    icon: Icons.pause_circle_outline,
                  ),
                ),
                const SizedBox(width: AppSpacing.s12),
                Expanded(
                  child: MetricCard(
                    label: 'Total System Users',
                    value: '${_dashboard?['totalUsers'] ?? 0}',
                    status: StatusKind.info,
                    icon: Icons.people_outline,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.s24),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    decoration: const InputDecoration(
                      hintText: 'Search schools by name or slug...',
                      prefixIcon: Icon(Icons.search),
                    ),
                    onChanged: (v) => setState(() => _query = v),
                  ),
                ),
                const SizedBox(width: AppSpacing.s16),
                IconButton(
                  icon: const Icon(Icons.refresh),
                  onPressed: _fetchDashboard,
                  tooltip: 'Refresh Metrics',
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.s16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.s16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Registered School Tenants', style: context.texts.titleMedium),
                    const SizedBox(height: AppSpacing.s12),
                    if (schools.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(AppSpacing.s24),
                        child: Center(child: Text('No school tenants match your filter.')),
                      )
                    else
                      ListView.separated(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: schools.length,
                        separatorBuilder: (context, index) => const Divider(),
                        itemBuilder: (ctx, idx) {
                          final school = schools[idx];
                          final status = school['status']?.toString() ?? 'active';
                          final isActive = status.toLowerCase() == 'active';
                          return ListTile(
                            leading: CircleAvatar(
                              child: Text(school['name'].toString().substring(0, 1).toUpperCase()),
                            ),
                            title: Text(school['name'] ?? ''),
                            subtitle: Text('Slug: ${school['slug']} • Created: ${school['createdAt']}'),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                StatusChip(
                                  label: status.toUpperCase(),
                                  kind: isActive ? StatusKind.success : StatusKind.warning,
                                ),
                                const SizedBox(width: AppSpacing.s12),
                                OutlinedButton(
                                  onPressed: () => _toggleSchoolStatus(school),
                                  child: Text(isActive ? 'Suspend' : 'Activate'),
                                ),
                              ],
                            ),
                          );
                        },
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

class _InviteSchoolModal extends ConsumerStatefulWidget {
  const _InviteSchoolModal({required this.onSuccess});

  final void Function(Map<String, dynamic> result) onSuccess;

  @override
  ConsumerState<_InviteSchoolModal> createState() => _InviteSchoolModalState();
}

class _InviteSchoolModalState extends ConsumerState<_InviteSchoolModal> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _slug = TextEditingController();
  final _adminEmail = TextEditingController();

  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _slug.dispose();
    _adminEmail.dispose();
    super.dispose();
  }

  void _autoGenerateSlug(String name) {
    final slug = name.toLowerCase().trim().replaceAll(RegExp(r'[^a-z0-9]+'), '-').replaceAll(RegExp(r'^-|-$'), '');
    _slug.text = slug;
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() {
      _submitting = true;
      _error = null;
    });

    try {
      final api = ref.read(apiClientProvider);
      final res = await api.post('/api/v1/super-admin/schools/invite', data: {
        'name': _name.text.trim(),
        'slug': _slug.text.trim(),
        'adminEmail': _adminEmail.text.trim(),
      }) as Map<String, dynamic>;

      if (mounted) {
        Navigator.pop(context);
        widget.onSuccess(res);
      }
    } on ApiProblem catch (e) {
      setState(() {
        _submitting = false;
        _error = e.detail;
      });
    } catch (_) {
      setState(() {
        _submitting = false;
        _error = 'Failed to register school. Try again.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Register & Invite New School'),
      content: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (_error != null) ...[
                AppBanner(kind: StatusKind.error, message: _error!),
                const SizedBox(height: AppSpacing.s12),
              ],
              AppTextField(
                controller: _name,
                label: 'School Name',
                required: true,
                onChanged: _autoGenerateSlug,
                validator: (v) => (v == null || v.trim().isEmpty) ? 'School name is required' : null,
              ),
              const SizedBox(height: AppSpacing.s12),
              AppTextField(
                controller: _slug,
                label: 'School Slug / Identifier',
                required: true,
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Slug is required' : null,
              ),
              const SizedBox(height: AppSpacing.s12),
              AppTextField(
                controller: _adminEmail,
                label: 'School Admin Email',
                required: true,
                keyboardType: TextInputType.emailAddress,
                validator: (v) => (v == null || !v.contains('@')) ? 'Valid email is required' : null,
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        AppButton(
          label: 'Send Invitation Link',
          busy: _submitting,
          onPressed: _submit,
        ),
      ],
    );
  }
}
