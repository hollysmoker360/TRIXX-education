import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/sync/offline_queue.dart';
import '../../../core/widgets/common.dart';

class SyncScreen extends ConsumerStatefulWidget {
  const SyncScreen({super.key});

  @override
  ConsumerState<SyncScreen> createState() => _SyncScreenState();
}

class _SyncScreenState extends ConsumerState<SyncScreen> {
  StreamSubscription<void>? _queueSub;
  StreamSubscription<SyncStatus>? _statusSub;

  @override
  void initState() {
    super.initState();
    final queue = ref.read(offlineQueueProvider);
    final service = ref.read(syncServiceProvider);
    _queueSub = queue.changes.listen((_) => setState(() => _tick++));
    _statusSub = service.statusStream.listen((_) => setState(() => _tick++));
    service.notifyQueueChanged();
  }

  @override
  void dispose() {
    _queueSub?.cancel();
    _statusSub?.cancel();
    super.dispose();
  }

  int _tick = 0;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final queue = ref.watch(offlineQueueProvider);
    final service = ref.watch(syncServiceProvider);
    final session = ref.watch(sessionProvider);
    final status = service.status;
    final pending = queue.pending();

    return RefreshIndicator(
      onRefresh: () => service.syncNow(),
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            title: s.syncCenter,
            action: FilledButton.tonalIcon(
              onPressed: status.syncing ? null : () => service.syncNow(),
              icon: const Icon(Icons.sync, size: 18),
              label: Text(s.syncNow),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (status.syncing) ...[
                  const LinearProgressIndicator(),
                  const SizedBox(height: 10),
                ],
                _row('Pending', '${pending.length}'),
                _row('Cursor', '${status.cursor}'),
                _row(
                  'Last synced',
                  status.lastSyncedAt == null
                      ? '—'
                      : formatDateTime(status.lastSyncedAt!),
                ),
                _row('School', session.membership?.schoolName ?? s.noSchool),
                if (status.lastError != null) ...[
                  const SizedBox(height: 8),
                  Card(
                    color: Theme.of(context).colorScheme.errorContainer,
                    child: ListTile(
                      leading: const Icon(Icons.cloud_off),
                      title: Text(status.lastError!),
                      subtitle: const Text(
                          'Changes are kept locally and pushed when back online.'),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (status.rejected.isNotEmpty) ...[
            SectionCard(
              title: S.of(context).rejectedChanges,
              child: Column(
                children: [
                  for (final rejected in status.rejected)
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(Icons.error_outline,
                          color: Theme.of(context).colorScheme.error),
                      title: Text(
                          '${rejected.op.entity} · ${rejected.op.type}'),
                      subtitle: Text(rejected.reason),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
          SectionCard(
            title: '${s.pendingChanges} (${pending.length})',
            child: pending.isEmpty
                ? Text(s.everythingSynced, style: Theme.of(context).textTheme.bodyMedium)
                : Column(
                    children: [
                      for (final op in pending)
                        ListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.cloud_upload_outlined),
                          title: Text('${op.entity} · ${op.type}'),
                          subtitle: Text([
                            if (op.fields['title'] != null) '${op.fields['title']}',
                            if (op.fields['body'] != null) '${op.fields['body']}',
                            if (op.fields['status'] != null) '${op.fields['status']}',
                            formatDateTime(op.clientTime),
                          ].join(' · ')),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
