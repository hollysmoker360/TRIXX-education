import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _subscriptionProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return await api.get('/api/v1/subscriptions/current') as Map<String, dynamic>;
});

final _entitlementsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return await api.get('/api/v1/subscriptions/entitlements') as Map<String, dynamic>;
});

final _catalogueProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/subscriptions/catalogue') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

final _historyProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/subscriptions/history') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

final _providerProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return await api.get('/api/v1/subscriptions/provider') as String;
});

class SubscriptionsScreen extends ConsumerStatefulWidget {
  const SubscriptionsScreen({super.key});

  @override
  ConsumerState<SubscriptionsScreen> createState() => _SubscriptionsScreenState();
}

class _SubscriptionsScreenState extends ConsumerState<SubscriptionsScreen> {
  String? _verifyingReference;
  bool _checkoutOpen = false;

  Future<void> _checkout(String planCode) async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final result = await api.post('/api/v1/subscriptions/checkout', data: {
        'planCode': planCode,
        'callbackUrl': 'http://localhost/subscriber/return',
      }) as Map<String, dynamic>;
      setState(() {
        _verifyingReference = result['reference'] as String?;
        _checkoutOpen = true;
      });
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  Future<void> _verify() async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final reference = _verifyingReference;
    if (reference == null || reference.isEmpty) return;
    try {
      await api.post('/api/v1/subscriptions/verify', data: {'reference': reference});
      setState(() => _checkoutOpen = false);
      ref.invalidate(_subscriptionProvider);
      ref.invalidate(_entitlementsProvider);
      ref.invalidate(_historyProvider);
      messenger.showSnackBar(
          const SnackBar(content: Text('Payment confirmed — subscription activated.')));
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Verification failed: ${e.detail}')));
    }
  }

  Future<void> _mutate(String method, String success) async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await api.post('/api/v1/subscriptions/$method');
      ref.invalidate(_subscriptionProvider);
      ref.invalidate(_entitlementsProvider);
      ref.invalidate(_historyProvider);
      messenger.showSnackBar(SnackBar(content: Text(success)));
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  void _confirmCancel() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(S.of(context).confirmCancel),
        content: Text(S.of(context).cancelConfirmText),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(S.of(context).cancel)),
          FilledButton(
              onPressed: () {
                Navigator.of(context).pop();
                _mutate('cancel', 'Subscription cancelled.');
              },
              child: Text(S.of(context).cancelSubscription)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final canManage = session.can('subscription.manage');
    final subscription = ref.watch(_subscriptionProvider);
    final entitlements = ref.watch(_entitlementsProvider);
    final catalogue = ref.watch(_catalogueProvider);
    final history = ref.watch(_historyProvider);
    final provider = ref.watch(_providerProvider);

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(_subscriptionProvider);
        ref.invalidate(_entitlementsProvider);
        ref.invalidate(_catalogueProvider);
        ref.invalidate(_historyProvider);
        ref.invalidate(_providerProvider);
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            title: s.subscription,
            child: AsyncView<Map<String, dynamic>>(
              value: subscription,
              onRetry: () => ref.invalidate(_subscriptionProvider),
              isEmpty: (data) => data.isEmpty,
              builder: (data) => Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _field(s.plan, '${data['planName'] ?? ''} (${data['planCode'] ?? ''})'),
                  _field(s.status, data['status'] ?? '',
                      valueColor: _statusColor(data['status'])),
                  _field(s.provider, provider.valueOrNull ?? '—'),
                  if (data['trialEndsAt'] != null)
                    _field('Trial ends', '${data['trialEndsAt']}'),
                  if (data['currentPeriodEnd'] != null)
                    _field('Period ends', '${data['currentPeriodEnd']}'),
                  if (data['billable'] == true)
                    _field('Access', 'Active — subscription grants access',
                        valueColor: Colors.green.shade700)
                  else
                    _field('Access', 'No active billing period',
                        valueColor: Colors.red.shade700),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          SectionCard(
            title: s.entitlements,
            child: AsyncView<Map<String, dynamic>>(
              value: entitlements,
              onRetry: () => ref.invalidate(_entitlementsProvider),
              isEmpty: (data) => data.isEmpty,
              builder: (data) => Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _field('Plan', '${data['planCode'] ?? ''}'),
                  _field(s.status, '${data['status'] ?? ''}'),
                  _field(s.maxStudents, '${data['maxStudents'] ?? '—'}'),
                  _field(s.maxTeachers, '${data['maxTeachers'] ?? '—'}'),
                  _field('Access', data['active'] == true ? 'Granted' : 'Not granted'),
                  const SizedBox(height: 8),
                  Text(s.features, style: Theme.of(context).textTheme.labelSmall),
                  Wrap(
                    spacing: 8,
                    children: (data['features'] as List? ?? const [])
                        .cast<String>()
                        .map((f) => Chip(label: Text(f)))
                        .toList(),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (canManage) ...[
            SectionCard(
              title: s.upgrade,
              child: AsyncView<List<Map<String, dynamic>>>(
                value: catalogue,
                onRetry: () => ref.invalidate(_catalogueProvider),
                isEmpty: (data) => data.isEmpty,
                builder: (plans) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final plan in plans)
                      ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        title: Text('${plan['name']} (${plan['code']})'),
                        subtitle: Text(
                            '${_money(plan['priceMinor'], plan['currency'])} · ${plan['intervalUnit']}'),
                        trailing: plan['current'] == true
                            ? Text(s.current, style: const TextStyle(fontSize: 12, color: Colors.green))
                            : OutlinedButton(
                                onPressed: _checkoutOpen ? null : () => _checkout('${plan['code']}'),
                                child: Text(s.checkout),
                              ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            SectionCard(
              title: s.paymentHistory,
              child: AsyncView<List<Map<String, dynamic>>>(
                value: history,
                onRetry: () => ref.invalidate(_historyProvider),
                isEmpty: (data) => data.isEmpty,
                builder: (payments) => Column(
                  children: [
                    for (final p in payments)
                      ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(
                          p['status'] == 'success'
                              ? Icons.check_circle
                              : Icons.error_outline,
                          color: p['status'] == 'success' ? Colors.green : Colors.red,
                        ),
                        title: Text(
                            '${p['provider']} · ${_money(p['amountMinor'], p['currency'])}'),
                        subtitle: Text(
                            '${p['status']} · ${p['reference'] ?? ''} · ${p['createdAt'] ?? ''}'),
                      ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 12),
          if (canManage) ...[
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _checkoutOpen ? null : _confirmCancel,
                    child: Text(s.cancelSubscription),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => _mutate('resume', 'Subscription resumed.'),
                    child: Text(s.resumeSubscription),
                  ),
                ),
              ],
            ),
          ],
          if (_checkoutOpen && _verifyingReference != null) ...[
            const SizedBox(height: 12),
            SectionCard(
              title: s.checkout,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                      'Pay with ${provider.valueOrNull ?? 'provider'}'),
                  const SizedBox(height: 8),
                  Text(_verifyingReference!,
                      style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                  const SizedBox(height: 8),
                  TextButton.icon(
                    onPressed: () async {
                      final url = Uri.tryParse(
                          'http://localhost:8080/mock/pay?reference=$_verifyingReference');
                      if (url != null) await launchUrl(url);
                    },
                    icon: const Icon(Icons.open_in_new, size: 18),
                    label: Text(s.openPaymentPage),
                  ),
                  const SizedBox(height: 8),
                  FilledButton(
                    onPressed: _verify,
                    child: Text(s.payNow),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _field(String label, String value, {Color? valueColor}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(label, style: Theme.of(context).textTheme.labelSmall),
          ),
          Expanded(child: Text(value, style: TextStyle(color: valueColor))),
        ],
      ),
    );
  }

  Color? _statusColor(dynamic status) {
    switch (status) {
      case 'active':
        return Colors.green.shade700;
      case 'trial':
        return Colors.blue.shade700;
      case 'past_due':
        return Colors.orange.shade700;
      case 'suspended':
        return Colors.red.shade700;
      case 'cancelled':
        return Colors.grey.shade700;
      default:
        return null;
    }
  }

  String _money(dynamic minor, dynamic currency) {
    final m = (minor as num?) ?? 0;
    final c = currency ?? 'KES';
    final units = m ~/ 100;
    final cents = m % 100;
    return '$c ${units.toString().padLeft(1)}.${cents.toString().padLeft(2, '0')}';
  }
}
