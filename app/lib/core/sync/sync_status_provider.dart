import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'offline_queue.dart';
import '../session/session.dart';

/// Global, read-only exposure of the sync status so any shell/app-bar can render an indicator
/// without each feature screen wiring its own stream subscription.
final syncStatusProvider =
    NotifierProvider<SyncStatusNotifier, SyncStatus>(SyncStatusNotifier.new);

/// Auto-sync on reconnect: when the device regains network connectivity, push pending operations
/// and pull the change feed. Only syncs once a school is selected.
final autoSyncProvider = Provider((ref) {
  final notifier = ref.read(syncStatusProvider.notifier);
  final session = ref.watch(sessionProvider);
  final subscription = Connectivity().onConnectivityChanged.listen((result) {
    final connected = result.isNotEmpty && !result.contains(ConnectivityResult.none);
    if (connected &&
        session.selectedSchoolId != null &&
        !session.initializing) {
      notifier.syncNow();
    }
  });
  ref.onDispose(() => subscription.cancel());
});

class SyncStatusNotifier extends Notifier<SyncStatus> {
  StreamSubscription<SyncStatus>? _statusSub;

  @override
  SyncStatus build() {
    final service = ref.watch(syncServiceProvider);
    _statusSub = service.statusStream.listen((next) {
      state = next;
    });
    ref.onDispose(() {
      _statusSub?.cancel();
    });
    service.notifyQueueChanged();
    return service.status;
  }

  /// Push pending operations and pull the change feed. [SyncService] guards against concurrent runs.
  Future<void> syncNow() async {
    final service = ref.read(syncServiceProvider);
    await service.syncNow();
  }
}
