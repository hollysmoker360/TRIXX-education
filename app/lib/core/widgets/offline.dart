import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/app_config.dart';
import '../l10n/strings.dart';
import '../sync/offline_queue.dart';
import '../sync/sync_status_provider.dart';
import '../theme/tokens.dart';
import 'surfaces.dart';

/// Current device connectivity (specification section 8.3).
final connectivityProvider = StreamProvider<List<ConnectivityResult>>(
  (ref) => Connectivity().onConnectivityChanged,
);

final isOnlineProvider = Provider<bool>((ref) {
  return ref.watch(connectivityProvider).maybeWhen(
        data: (results) => results.any((r) => r != ConnectivityResult.none),
        orElse: () => true,
      );
});

/// True when the last sync produced rejections or conflicts the user must
/// resolve before the affected records are settled.
final needsReviewProvider = Provider<bool>(
  (ref) => ref.watch(syncStatusProvider).rejected.isNotEmpty,
);

/// Offline behaviour is a first-class part of the interface, not an error mode
/// (specification section 8.3). Renders nothing when online with nothing to
/// report.
class OfflineBanner extends ConsumerWidget {
  const OfflineBanner({super.key, this.margin});

  final EdgeInsets? margin;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final online = ref.watch(isOnlineProvider);
    final status = ref.watch(syncStatusProvider);
    final needsReview = ref.watch(needsReviewProvider);

    final (kind, message, action) = _describe(context, s, online, status, needsReview);
    if (message == null) return const SizedBox.shrink();

    return Padding(
      padding: margin ?? const EdgeInsets.fromLTRB(AppSpacing.s16, AppSpacing.s8, AppSpacing.s16, 0),
      child: AppBanner(
        kind: kind,
        message: message,
        action: action,
      ),
    );
  }

  (StatusKind, String?, Widget?) _describe(
    BuildContext context,
    S s,
    bool online,
    SyncStatus status,
    bool needsReview,
  ) {
    if (!online) {
      final suffix = status.pending > 0 ? ' ${status.pending} ${s.pendingChanges}' : '';
      return (
        StatusKind.warning,
        '${s.offlineSaved}${suffix.isEmpty ? '' : '.$suffix'}',
        TextButton(
          onPressed: () => _openDetail(context),
          child: Text(s.details),
        ),
      );
    }
    if (needsReview) {
      return (
        StatusKind.error,
        s.needsReview,
        TextButton(
          onPressed: () => _openDetail(context),
          child: Text(s.review),
        ),
      );
    }
    if (status.syncing && status.pending > 0) {
      return (StatusKind.info, '${s.syncingChanges} ${status.pending}', null);
    }
    if (status.lastError != null) {
      return (
        StatusKind.error,
        s.syncFailed,
        TextButton(onPressed: () => _openDetail(context), child: Text(s.details)),
      );
    }
    return (StatusKind.info, null, null);
  }

  void _openDetail(BuildContext context) => showSyncDetailSheet(context);
}

/// Detail sheet from the sync indicator: last successful sync, pending
/// operations, failures, and the connected server.
Future<void> showSyncDetailSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => const _SyncDetailSheet(),
  );
}

class _SyncDetailSheet extends ConsumerWidget {
  const _SyncDetailSheet();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final status = ref.watch(syncStatusProvider);
    final online = ref.watch(isOnlineProvider);

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(AppSpacing.s16, 0, AppSpacing.s16, AppSpacing.s16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(s.syncCenter, style: context.texts.titleLarge),
            const SizedBox(height: AppSpacing.s12),
            _row(context, s.connection, online ? s.online : s.offline),
            _row(context, s.server, AppConfig.serverLabel),
            _row(
              context,
              s.lastSynced,
              status.lastSyncedAt == null ? s.never : _ago(context, status.lastSyncedAt!),
            ),
            _row(context, s.pendingChanges, '${status.pending}'),
            if (status.lastError != null) _row(context, s.lastError, status.lastError!),
            if (status.rejected.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.s12),
              Text(s.needsReview, style: context.texts.titleSmall),
              const SizedBox(height: AppSpacing.s8),
              for (final rejected in status.rejected)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.report_gmailerrorred, color: context.scheme.error),
                  title: Text('${rejected.op.entity} · ${rejected.op.type}'),
                  subtitle: Text(rejected.reason),
                ),
            ],
            const SizedBox(height: AppSpacing.s16),
            FilledButton.icon(
              onPressed: status.syncing
                  ? null
                  : () => ref.read(syncStatusProvider.notifier).syncNow(),
              icon: const Icon(Icons.sync),
              label: Text(s.syncNow),
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(BuildContext context, String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 140,
              child: Text(label, style: context.texts.labelMedium?.copyWith(color: context.tokens.textSecondary)),
            ),
            Expanded(child: Text(value, style: context.texts.bodyMedium)),
          ],
        ),
      );

  String _ago(BuildContext context, DateTime at) {
    final diff = DateTime.now().difference(at);
    if (diff.inSeconds < 60) return S.of(context).justNow;
    if (diff.inMinutes < 60) return '${diff.inMinutes}${S.of(context).minutesAgo}';
    if (diff.inHours < 24) return '${diff.inHours}${S.of(context).hoursAgo}';
    return '${diff.inDays}${S.of(context).daysAgo}';
  }
}
