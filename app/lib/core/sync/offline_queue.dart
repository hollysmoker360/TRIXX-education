import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../ids.dart';
import '../networking/api_client.dart';
import '../networking/api_problem.dart';
import '../networking/token_store.dart';
import '../session/session.dart';

/// One queued mutation captured while offline (or when a push failed).
/// Mirrors the backend sync operation shape: entity/type/fields/clientTime.
class PendingOp {
  PendingOp({
    required this.opId,
    required this.entity,
    required this.entityId,
    required this.type,
    this.baseVersion,
    this.fields = const {},
    DateTime? clientTime,
  }) : clientTime = clientTime ?? DateTime.now().toUtc();

  final String opId;
  final String entity; // task | message | attendance_event
  final String entityId;
  final String type; // create | update | delete
  final int? baseVersion;
  final Map<String, dynamic> fields;
  final DateTime clientTime;

  Map<String, dynamic> toJson() => {
        'opId': opId,
        'entity': entity,
        'entityId': entityId,
        'type': type,
        'baseVersion': baseVersion,
        'fields': fields,
        'clientTime': clientTime.toIso8601String(),
      };

  factory PendingOp.fromJson(Map<String, dynamic> json) => PendingOp(
        opId: '${json['opId']}',
        entity: '${json['entity']}',
        entityId: '${json['entityId']}',
        type: '${json['type']}',
        baseVersion: json['baseVersion'] as int?,
        fields: Map<String, dynamic>.from(json['fields'] as Map? ?? {}),
        clientTime: DateTime.tryParse('${json['clientTime']}')?.toUtc(),
      );
}

class RejectedOp {
  RejectedOp(this.op, this.reason);
  final PendingOp op;
  final String reason;
}

class SyncStatus {
  const SyncStatus({
    this.pending = 0,
    this.syncing = false,
    this.lastSyncedAt,
    this.lastError,
    this.rejected = const [],
    this.cursor = 0,
  });

  final int pending;
  final bool syncing;
  final DateTime? lastSyncedAt;
  final String? lastError;
  final List<RejectedOp> rejected;
  final int cursor;

  SyncStatus copyWith({
    int? pending,
    bool? syncing,
    DateTime? lastSyncedAt,
    String? lastError,
    List<RejectedOp>? rejected,
    int? cursor,
    bool clearError = false,
  }) =>
      SyncStatus(
        pending: pending ?? this.pending,
        syncing: syncing ?? this.syncing,
        lastSyncedAt: lastSyncedAt ?? this.lastSyncedAt,
        lastError: clearError ? null : (lastError ?? this.lastError),
        rejected: rejected ?? this.rejected,
        cursor: cursor ?? this.cursor,
      );
}

/// Durable offline queue + change-feed cursor, persisted per school.
class OfflineQueue {
  OfflineQueue(this._tokens, this._schoolId);

  final TokenStore _tokens;
  final _changes = StreamController<void>.broadcast();

  Stream<void> get changes => _changes.stream;

  final String _schoolId;

  List<PendingOp> pending() {
    final raw = _tokens.getRawPendingOps(_schoolId);
    if (raw == null || raw.isEmpty) return [];
    try {
      final list = jsonDecode(raw) as List;
      return list
          .map((e) => PendingOp.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (_) {
      return [];
    }
  }

  void enqueue(PendingOp op) {
    final ops = pending()..add(op);
    _persist(ops);
  }

  void remove(Iterable<String> opIds) {
    final ids = opIds.toSet();
    _persist(pending().where((op) => !ids.contains(op.opId)).toList());
  }

  void _persist(List<PendingOp> ops) {
    _tokens.setRawPendingOps(
        _schoolId, ops.isEmpty ? null : jsonEncode(ops.map((o) => o.toJson()).toList()));
    _changes.add(null);
  }

  int get cursor => _tokens.getSyncCursor(_schoolId);

  void setCursor(int value) {
    _tokens.setSyncCursor(_schoolId, value);
    _changes.add(null);
  }
}

/// Pushes queued operations and pulls the change feed.
class SyncService {
  SyncService(this._api, this._queue, this._tokens);

  final ApiClient _api;
  final OfflineQueue _queue;
  final TokenStore _tokens;

  final _controller = StreamController<SyncStatus>.broadcast();
  SyncStatus _status = const SyncStatus();

  Stream<SyncStatus> get statusStream => _controller.stream;
  SyncStatus get status => _status;

  void _emit(SyncStatus next) {
    _status = next;
    _controller.add(next);
  }

  void notifyQueueChanged() {
    _emit(_status.copyWith(pending: _queue.pending().length, cursor: _queue.cursor));
  }

  /// Push all pending operations in one batch. Applied/duplicate ops are removed;
  /// rejected ops are reported (and removed — they will never succeed unchanged).
  Future<void> pushPending() async {
    final ops = _queue.pending();
    if (ops.isEmpty) return;
    _emit(_status.copyWith(syncing: true, clearError: true));
    try {
      final result = await _api.post('/api/v1/sync/push', data: {
        'deviceId': _tokens.deviceId,
        'batchId': newUuid(),
        'operations': ops.map((o) => o.toJson()).toList(),
      }) as Map<String, dynamic>;

      final results = (result['results'] as List? ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      final finishedIds = <String>[];
      final rejected = <RejectedOp>[];
      for (final r in results) {
        final opId = '${r['opId']}';
        final outcome = '${r['result']}';
        if (outcome == 'applied' || outcome == 'duplicate') {
          finishedIds.add(opId);
        } else if (outcome == 'rejected' || outcome == 'conflict') {
          finishedIds.add(opId);
          final op = ops.firstWhere((o) => o.opId == opId,
              orElse: () => ops.first);
          rejected.add(RejectedOp(op, '${r['reason'] ?? outcome}'));
        }
      }
      _queue.remove(finishedIds);
      _emit(_status.copyWith(
        syncing: false,
        pending: _queue.pending().length,
        lastSyncedAt: DateTime.now(),
        rejected: rejected,
        clearError: true,
      ));
    } on ApiProblem catch (e) {
      _emit(_status.copyWith(syncing: false, lastError: e.detail));
    }
  }

  /// Pull the change feed from the stored cursor. Returns the raw changes for the
  /// caller to cache; advances the cursor.
  Future<List<Map<String, dynamic>>> pullChanges() async {
    try {
      final result = await _api.get('/api/v1/sync/pull', query: {
        'cursor': _queue.cursor,
        'deviceId': _tokens.deviceId,
      }) as Map<String, dynamic>;
      final cursor = (result['cursor'] as num?)?.toInt() ?? _queue.cursor;
      _queue.setCursor(cursor);
      final changes = (result['changes'] as List? ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      _emit(_status.copyWith(cursor: cursor, lastSyncedAt: DateTime.now(), clearError: true));
      return changes;
    } on ApiProblem catch (e) {
      _emit(_status.copyWith(lastError: e.detail));
      return const [];
    }
  }

  bool _running = false;

  Future<void> syncNow() async {
    if (_running) return;
    _running = true;
    _emit(_status.copyWith(syncing: true, clearError: true));
    try {
      await pushPending();
      if (_status.lastError == null) await pullChanges();
    } finally {
      _running = false;
      _emit(_status.copyWith(syncing: false));
    }
  }
}

final offlineQueueProvider = Provider<OfflineQueue>((ref) {
  final scope = ref.watch(sessionProvider.select((s) => (s.user?.id, s.selectedSchoolId)));
  return OfflineQueue(ref.watch(tokenStoreProvider), '${scope.$1}.${scope.$2}');
});

final syncServiceProvider = Provider<SyncService>((ref) {
  return SyncService(
    ref.watch(apiClientProvider),
    ref.watch(offlineQueueProvider),
    ref.watch(tokenStoreProvider),
  );
});
