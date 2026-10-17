import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/chat/chat_socket.dart';
import '../../../core/ids.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/sync/offline_queue.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../data/chat_providers.dart';

final _channelsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/chat/channels');
  return (data as List).cast<Map<String, dynamic>>();
});

/// Channel list. Each row shows the last message and the unread count.
class ChatScreen extends ConsumerWidget {
  const ChatScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final channels = ref.watch(_channelsProvider);
    return AsyncView<List<Map<String, dynamic>>>(
      value: channels,
      onRetry: () => ref.invalidate(_channelsProvider),
      isEmpty: (rows) => rows.isEmpty,
      builder: (rows) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(_channelsProvider),
        child: ListView.builder(
          padding: const EdgeInsets.all(AppSpacing.s16),
          itemCount: rows.length,
          itemBuilder: (context, i) {
            final channel = rows[i];
            final unread = (channel['unreadCount'] as num?)?.toInt() ?? 0;
            final preview = channel['lastMessage'] as Map<String, dynamic>?;
            return Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.s12),
              child: Card(
                child: ListTile(
                  leading: CircleAvatar(
                    child: Icon(channel['staffOnly'] == true
                        ? Icons.lock_outline
                        : Icons.forum_outlined),
                  ),
                  title: Text('${channel['name']}'),
                  subtitle: Text(
                    preview == null
                        ? '${channel['channelType']}'
                        : '${preview['senderName']}: ${preview['body']}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: unread > 0
                      ? Badge.count(
                          count: unread,
                          child: const SizedBox(width: 24, height: 24),
                        )
                      : null,
                  onTap: () async {
                    await context.push('/chat/${channel['id']}',
                        extra: '${channel['name']}');
                    ref.invalidate(_channelsProvider);
                  },
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

/// A single channel.
///
/// Messages arrive over STOMP while the socket is up, so the conversation needs
/// no polling; when the socket is down the screen falls back to the 15-second
/// refresh it used before, and says so in the app bar. Sending prefers the socket
/// and falls back to REST (and then the offline queue) — all three paths use the
/// same client-generated `clientId`, which the server treats as an idempotency
/// key, so a retry can never double-post.
class ChannelScreen extends ConsumerStatefulWidget {
  const ChannelScreen({super.key, required this.channelId, required this.channelName});

  final String channelId;
  final String channelName;

  @override
  ConsumerState<ChannelScreen> createState() => _ChannelScreenState();
}

class _ChannelScreenState extends ConsumerState<ChannelScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final _streams = <StreamSubscription<ChatEvent>>[];

  /// Ordered conversation. Optimistic (not yet confirmed) sends live here too.
  final List<Map<String, dynamic>> _messages = <Map<String, dynamic>>[];

  /// Message id *or* clientId → index in [_messages], for de-duplication.
  final Map<String, int> _indexByKey = <String, int>{};

  ChatSocket? _socket;
  String? _channelSubscription;

  /// Inbox subscriptions are shared with the channel topic so a screen can also
  /// receive delivery confirmations; all three are released on dispose.
  String? _ackSubscription;
  String? _errorSubscription;
  Object? _error;
  bool _loading = true;
  bool _sending = false;
  bool _realtime = false;

  Timer? _fallbackPoll;
  Timer? _typingClear;
  Timer? _readDebounce;
  int _pendingReadSeq = 0;
  String? _typingName;
  DateTime _typingSentAt = DateTime.fromMillisecondsSinceEpoch(0);

  String get _channelId => widget.channelId;

  String? get _myMembershipId => ref.read(sessionProvider).membership?.membershipId;

  @override
  void initState() {
    super.initState();
    _input.addListener(_onInputChanged);
    unawaited(_load());
    _startRealtime();
  }

  @override
  void dispose() {
    for (final stream in _streams) {
      unawaited(stream.cancel());
    }
    // The socket itself is shared and outlives this screen; only our
    // subscriptions are ours to remove.
    final socket = _socket;
    if (socket != null) {
      for (final id in [_channelSubscription, _ackSubscription, _errorSubscription]) {
        if (id != null) socket.unsubscribe(id);
      }
    }
    _fallbackPoll?.cancel();
    _typingClear?.cancel();
    _readDebounce?.cancel();
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Real-time transport
  // ---------------------------------------------------------------------------

  void _startRealtime() {
    final socket = ref.read(chatSocketProvider);
    final schoolId = ref.read(sessionProvider).membership?.schoolId;
    if (socket == null || schoolId == null) {
      _startFallbackPolling();
      return;
    }

    _socket = socket;
    _streams.add(socket.events.listen(_onSocketEvent));
    _channelSubscription = socket.subscribe(ChatTopics.channel(schoolId, _channelId));
    _ackSubscription = socket.subscribe(ChatTopics.ack);
    _errorSubscription = socket.subscribe(ChatTopics.errors);
    unawaited(socket.connect());
  }

  void _onSocketEvent(ChatEvent event) {
    switch (event) {
      case ChatConnectionChanged(:final state):
        final live = state == ChatConnection.connected;
        if (live == _realtime) return;
        setState(() => _realtime = live);
        if (live) {
          _stopFallbackPolling();
          // Pick up anything sent while the socket was down.
          unawaited(_load(silent: true));
        } else {
          _startFallbackPolling();
        }
      case ChatMessageEvent(:final message):
        _upsert(message);
        final seq = (message['seq'] as num?)?.toInt() ?? 0;
        if (seq > 0) _scheduleRead(seq);
      case ChatAckEvent(:final message):
        _upsert(message);
      case ChatTypingEvent(:final membershipId, :final displayName):
        if (membershipId == _myMembershipId) return;
        _showTyping(displayName);
      case ChatErrorEvent(:final message):
        if (mounted) showAppSnack(context, message, kind: StatusKind.error);
    }
  }

  void _startFallbackPolling() {
    _fallbackPoll?.cancel();
    _fallbackPoll = Timer.periodic(const Duration(seconds: 15), (_) {
      if (_realtime) return;
      unawaited(_load(silent: true));
    });
  }

  void _stopFallbackPolling() {
    _fallbackPoll?.cancel();
    _fallbackPoll = null;
  }

  // ---------------------------------------------------------------------------
  // History
  // ---------------------------------------------------------------------------

  Future<void> _load({bool silent = false}) async {
    final api = ref.read(apiClientProvider);
    if (!silent) setState(() => _loading = true);
    try {
      final page = await api.get(
        '/api/v1/chat/channels/$_channelId/messages',
        query: {'limit': 50},
      ) as Map<String, dynamic>;
      final messages = (page['messages'] as List? ?? const [])
          .cast<Map<String, dynamic>>();

      if (!mounted) return;
      setState(() {
        // Realtime messages that arrived while the request was in flight are
        // kept: merge the server page over them by id/clientId.
        final live = _messages.where((m) => m['pending'] == true).toList();
        _messages
          ..clear()
          ..addAll(messages)
          ..addAll(live);
        _reindex();
        _error = null;
        _loading = false;
      });
      _scrollToEnd();

      final lastSeq = (page['lastSeq'] as num?)?.toInt() ?? 0;
      if (lastSeq > 0) await _markRead(lastSeq);
    } on ApiProblem catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  /// Read receipts are best effort: a failure must never disturb the thread.
  Future<void> _markRead(int seq) async {
    try {
      await ref.read(apiClientProvider).post(
        '/api/v1/chat/channels/$_channelId/read',
        data: {'seq': seq},
      );
    } on ApiProblem {
      // ignored
    }
  }

  void _scheduleRead(int seq) {
    _pendingReadSeq = seq > _pendingReadSeq ? seq : _pendingReadSeq;
    _readDebounce?.cancel();
    _readDebounce = Timer(const Duration(milliseconds: 800), () {
      final seq = _pendingReadSeq;
      if (seq > 0) unawaited(_markRead(seq));
    });
  }

  // ---------------------------------------------------------------------------
  // Mutation
  // ---------------------------------------------------------------------------

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty) return;

    final clientId = newUuid();
    final senderName = ref.read(sessionProvider).user?.displayName ?? '';
    _input.clear();

    setState(() {
      _sending = true;
      _messages.add({
        'clientId': clientId,
        'body': text,
        'senderName': senderName,
        'sentAt': DateTime.now().toUtc().toIso8601String(),
        'pending': true,
      });
      _indexByKey[clientId] = _messages.length - 1;
    });
    _scrollToEnd();

    final socket = _socket;
    if (socket != null &&
        socket.isConnected &&
        socket.sendJson(ChatTopics.send(_channelId), {
          'clientId': clientId,
          'body': text,
        })) {
      setState(() => _sending = false);
      // The server broadcasts to the topic on commit, which clears `pending`.
      // If that never arrives (socket died mid-send) fall back to REST, which
      // is safe because the clientId makes the send idempotent.
      Timer(const Duration(seconds: 6), () {
        if (!mounted) return;
        final unconfirmed = _messages.any(
          (m) => '${m['clientId']}' == clientId && m['pending'] == true,
        );
        if (unconfirmed) unawaited(_sendViaRest(text, clientId));
      });
      return;
    }

    await _sendViaRest(text, clientId);
  }

  Future<void> _sendViaRest(String text, String clientId) async {
    if (!mounted) return;
    final s = S.of(context);
    final api = ref.read(apiClientProvider);
    final queue = ref.read(offlineQueueProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await api.post('/api/v1/chat/channels/$_channelId/messages', data: {
        'clientId': clientId,
        'body': text,
      });
      await _load(silent: true);
    } on ApiProblem catch (e) {
      if (e.isOffline) {
        queue.enqueue(PendingOp(
          opId: newUuid(),
          entity: 'message',
          entityId: clientId,
          type: 'create',
          fields: {
            'channelId': _channelId,
            'clientId': clientId,
            'body': text,
          },
        ));
        messenger.showSnackBar(SnackBar(content: Text(s.offlineQueued)));
      } else {
        // The queued entry must not linger as pending when the server refused.
        _removePending(clientId);
        messenger.showSnackBar(SnackBar(content: Text(e.detail)));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  void _onInputChanged() {
    final socket = _socket;
    if (socket == null || !socket.isConnected) return;
    if (_input.text.trim().isEmpty) return;
    final now = DateTime.now();
    if (now.difference(_typingSentAt) < const Duration(seconds: 2)) return;
    _typingSentAt = now;
    socket.sendJson(ChatTopics.typing(_channelId), const <String, dynamic>{});
  }

  // ---------------------------------------------------------------------------
  // Local state helpers
  // ---------------------------------------------------------------------------

  void _upsert(Map<String, dynamic> message) {
    if (!mounted) return;
    final id = message['id'] == null ? null : '${message['id']}';
    final clientId = message['clientId'] == null ? null : '${message['clientId']}';
    var index = id == null ? null : _indexByKey[id];
    index ??= clientId == null ? null : _indexByKey[clientId];

    setState(() {
      if (index != null && index < _messages.length) {
        _messages[index] = <String, dynamic>{..._messages[index], ...message, 'pending': false};
      } else {
        _messages.add(<String, dynamic>{...message, 'pending': false});
      }
      _reindex();
    });
    _scrollToEnd();
  }

  void _removePending(String clientId) {
    if (!mounted) return;
    setState(() {
      _messages.removeWhere(
        (m) => '${m['clientId']}' == clientId && m['pending'] == true,
      );
      _reindex();
    });
  }

  void _reindex() {
    _indexByKey.clear();
    for (var i = 0; i < _messages.length; i++) {
      final key = _messages[i]['id'] ?? _messages[i]['clientId'];
      if (key != null) _indexByKey['$key'] = i;
    }
  }

  void _showTyping(String? name) {
    setState(() => _typingName = name);
    _typingClear?.cancel();
    _typingClear = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _typingName = null);
    });
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.jumpTo(_scroll.position.maxScrollExtent);
    });
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final myMembershipId = session.membership?.membershipId;
    final queue = ref.watch(offlineQueueProvider);

    if (_error != null && _messages.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.channelName)),
        body: ErrorView(error: _error!, onRetry: _load),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(widget.channelName),
            Text(
              _realtime ? s.chatLive : s.chatPolling,
              style: context.texts.labelSmall?.copyWith(
                color: context.tokens.textSecondary,
                fontWeight: FontWeight.w400,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: s.refresh,
            icon: const Icon(Icons.refresh),
            onPressed: _load,
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _loading && _messages.isEmpty
                ? const LoadingSkeleton(rows: 6)
                : StreamBuilder<void>(
                    stream: queue.changes,
                    builder: (context, _) {
                      final queued = queue
                          .pending()
                          .where((op) =>
                              op.entity == 'message' &&
                              op.type == 'create' &&
                              '${op.fields['channelId']}' == _channelId)
                          .toList();
                      final total = _messages.length + queued.length;
                      if (total == 0 && _typingName == null) {
                        return Center(
                          child: Text(s.emptyList, style: context.texts.bodyMedium),
                        );
                      }
                      return ListView.builder(
                        controller: _scroll,
                        padding: const EdgeInsets.all(AppSpacing.s12),
                        itemCount: total + (_typingName == null ? 0 : 1),
                        itemBuilder: (context, i) {
                          if (i < _messages.length) {
                            final message = _messages[i];
                            return _MessageBubble(
                              message: message,
                              mine: '${message['senderMembershipId']}' == '$myMembershipId',
                            );
                          }
                          if (i - _messages.length < queued.length) {
                            final op = queued[i - _messages.length];
                            return _MessageBubble(
                              message: {
                                'body': op.fields['body'],
                                'senderName': session.user?.displayName ?? '',
                                'pending': true,
                              },
                              mine: true,
                            );
                          }
                          return _TypingBubble(
                            label: s.typingNow(_typingName ?? ''),
                          );
                        },
                      );
                    },
                  ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.s12,
                AppSpacing.s4,
                AppSpacing.s12,
                AppSpacing.s8,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _input,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _send(),
                      decoration: InputDecoration(hintText: s.typeMessage),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.s8),
                  IconButton.filled(
                    tooltip: s.send,
                    onPressed: _sending ? null : _send,
                    icon: const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A chat bubble. Pending sends carry an upload icon until the server confirms.
class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, required this.mine});

  final Map<String, dynamic> message;
  final bool mine;

  @override
  Widget build(BuildContext context) {
    final scheme = context.scheme;
    final s = S.of(context);
    final pending = message['pending'] == true;
    final deleted = message['deletedAt'] != null;
    final body = deleted ? s.messageDeleted : '${message['body']}';

    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 3),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.s12,
          vertical: AppSpacing.s8,
        ),
        constraints: const BoxConstraints(maxWidth: 480),
        decoration: BoxDecoration(
          color: mine ? scheme.primaryContainer : scheme.surfaceContainerHighest,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(AppRadius.md),
            topRight: const Radius.circular(AppRadius.md),
            bottomLeft: Radius.circular(mine ? AppRadius.md : AppRadius.sm),
            bottomRight: Radius.circular(mine ? AppRadius.sm : AppRadius.md),
          ),
        ),
        child: Column(
          crossAxisAlignment: mine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            if (!mine)
              Text(
                '${message['senderName']}',
                style: context.texts.labelSmall?.copyWith(fontWeight: FontWeight.w600),
              ),
            Text(
              body,
              style: context.texts.bodyMedium?.copyWith(
                fontStyle: deleted ? FontStyle.italic : null,
              ),
            ),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (pending) ...const [
                  Icon(Icons.cloud_upload_outlined, size: 12),
                  SizedBox(width: AppSpacing.s4),
                ],
                Text(_time(message['sentAt']), style: context.texts.labelSmall),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _time(dynamic value) {
    final parsed = DateTime.tryParse('$value');
    if (parsed == null) return '';
    final local = parsed.toLocal();
    return '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}';
  }
}

/// "Someone is typing…" — an animated three-dot placeholder.
class _TypingBubble extends StatelessWidget {
  const _TypingBubble({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Semantics(
        liveRegion: true,
        label: label,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 3),
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.s12,
            vertical: AppSpacing.s8,
          ),
          decoration: BoxDecoration(
            color: context.scheme.surfaceContainerHighest,
            borderRadius: const BorderRadius.only(
              topLeft: Radius.circular(AppRadius.md),
              topRight: Radius.circular(AppRadius.md),
              bottomLeft: Radius.circular(AppRadius.sm),
              bottomRight: Radius.circular(AppRadius.md),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const _TypingDots(),
              const SizedBox(width: AppSpacing.s8),
              Text(
                label,
                style: context.texts.labelSmall?.copyWith(
                  color: context.tokens.textSecondary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TypingDots extends StatefulWidget {
  const _TypingDots();

  @override
  State<_TypingDots> createState() => _TypingDotsState();
}

class _TypingDotsState extends State<_TypingDots> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 900),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.maybeOf(context)?.disableAnimations ?? false) {
      return Text('…', style: context.texts.labelSmall);
    }
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var i = 0; i < 3; i++) ...[
            if (i > 0) const SizedBox(width: 3),
            Opacity(
              // Dots pulse a third of a cycle apart.
              opacity: 0.25 +
                  0.75 *
                      (0.5 +
                              0.5 *
                                  math.sin(_controller.value * 2 * math.pi -
                                      i * (math.pi / 2)))
                          .clamp(0.0, 1.0),
              child: Container(
                width: 5,
                height: 5,
                decoration: BoxDecoration(
                  color: context.tokens.textSecondary,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
