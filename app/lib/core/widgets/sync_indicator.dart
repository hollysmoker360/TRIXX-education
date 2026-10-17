import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../l10n/strings.dart';
import '../sync/sync_status_provider.dart';
import '../theme/tokens.dart';
import 'offline.dart';

/// Compact sync status control for the app bar. Tapping it opens the sync
/// detail sheet (last successful sync, pending operations, failures, and the
/// connected server) as described in specification section 8.3.
class SyncIndicator extends ConsumerWidget {
  const SyncIndicator({super.key, this.onOpenDetail});

  final VoidCallback? onOpenDetail;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final status = ref.watch(syncStatusProvider);
    final pending = status.pending;
    final hasError = status.lastError != null;
    final needsReview = status.rejected.isNotEmpty;

    final tooltip = status.syncing
        ? s.syncingLabel
        : hasError
            ? status.lastError!
            : needsReview
                ? s.needsReview
                : pending > 0
                    ? '$pending ${s.pendingChanges}'
                    : s.upToDate;

    final color = hasError || needsReview
        ? context.scheme.error
        : pending > 0
            ? context.tokens.warning
            : context.tokens.success;

    return Tooltip(
      message: tooltip,
      child: IconButton(
        onPressed: () =>
            onOpenDetail == null ? showSyncDetailSheet(context) : onOpenDetail!(),
        icon: status.syncing
            ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Semantics(
                label: tooltip,
                child: Badge(
                  isLabelVisible: pending > 0,
                  label: Text('$pending'),
                  backgroundColor: color,
                  child: Icon(
                    hasError || needsReview ? Icons.sync_problem : Icons.sync_outlined,
                    color: color,
                  ),
                ),
              ),
      ),
    );
  }
}
