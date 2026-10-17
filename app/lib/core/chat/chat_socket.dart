import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

import 'stomp.dart';

/// Connection lifecycle as the UI needs to describe it.
enum ChatConnection {
  idle,

  /// Opening the socket and completing the STOMP handshake.
  connecting,

  /// Handshake complete; frames flow both ways.
  connected,

  /// The socket dropped and a reconnect is scheduled or in flight.
  reconnecting,

  /// Deliberately closed (screen disposed, signed out).
  closed,
}

/// Events a [ChatSocket] publishes to whoever is listening.
sealed class ChatEvent {
  const ChatEvent();
}

class ChatConnectionChanged extends ChatEvent {
  const ChatConnectionChanged(this.state, {this.attempt});

  final ChatConnection state;

  /// Reconnect attempt number (1 for the first retry), when [state] is
  /// [ChatConnection.reconnecting].
  final int? attempt;
}

/// A chat message arrived on a subscribed channel.
class ChatMessageEvent extends ChatEvent {
  const ChatMessageEvent(this.message);

  /// The server's `MessageView` payload.
  final Map<String, dynamic> message;
}

/// Somebody on the channel started typing.
class ChatTypingEvent extends ChatEvent {
  const ChatTypingEvent({
    required this.channelId,
    required this.membershipId,
    this.displayName,
  });

  final String channelId;
  final String membershipId;
  final String? displayName;
}

/// The server confirmed a message the client sent (deduplicated by clientId).
class ChatAckEvent extends ChatEvent {
  const ChatAckEvent(this.message);

  final Map<String, dynamic> message;
}

/// The server rejected a frame (validation, permission, too large…).
class ChatErrorEvent extends ChatEvent {
  const ChatErrorEvent({required this.code, required this.message});

  final String code;
  final String message;
}

/// STOMP destinations used by chat. Kept in one place so the client and the
/// backend contract (`ChatEvents`, `ChatStompController`) stay legible together.
abstract final class ChatTopics {
  /// Broadcast topic for a channel: `/topic/school.{schoolId}.channel.{channelId}`.
  static String channel(String schoolId, String channelId) =>
      '/topic/school.$schoolId.channel.$channelId';

  /// Send a message: `/app/channels/{channelId}/send`.
  static String send(String channelId) => '/app/channels/$channelId/send';

  /// Signal that the user is typing: `/app/channels/{channelId}/typing`.
  static String typing(String channelId) => '/app/channels/$channelId/typing';

  /// Per-user delivery confirmation queue.
  static const ack = '/user/queue/chat.ack';

  /// Per-user error queue.
  static const errors = '/user/queue/chat.errors';
}

/// A small STOMP 1.2 client over a single WebSocket, tuned for chat.
///
/// Responsibilities: the handshake, subscription bookkeeping (so a reconnect
/// restores every subscription), heartbeats in both directions, dead-connection
/// detection, and reconnect with capped exponential backoff. It carries nothing
/// domain-specific — callers send and receive plain JSON maps.
///
/// The client never crashes the UI on a socket problem: a failure simply moves
/// the connection state through [ChatConnection.reconnecting], which is the
/// signal for callers to fall back to polling.
class ChatSocket {
  ChatSocket({
    required this.baseUrl,
    required this.schoolId,
    required this.tokenProvider,
    this.path = '/ws',
    this.heartbeat = const Duration(seconds: 10),
    this.handshakeTimeout = const Duration(seconds: 10),
    this.maxReconnectDelay = const Duration(seconds: 15),
  });

  /// HTTP(S) base URL of the API; the socket scheme is derived from it.
  final String baseUrl;

  /// School the socket is scoped to (`X-School-Id`).
  final String schoolId;

  /// Web path of the STOMP endpoint.
  final String path;

  /// Negotiated heartbeat interval. The backend asks for 10s in both directions.
  final Duration heartbeat;

  /// How long to wait for the transport to open and the STOMP handshake to
  /// complete before giving up on an attempt.
  final Duration handshakeTimeout;

  final Duration maxReconnectDelay;

  /// Supplies the bearer token used for the handshake. Read on every connect so
  /// a refreshed token is picked up by the next reconnect.
  final Future<String?> Function() tokenProvider;

  final StreamController<ChatEvent> _events = StreamController<ChatEvent>.broadcast();
  final StompDecoder _decoder = StompDecoder();

  /// Subscription id → destination, replayed after every reconnect.
  final Map<String, String> _subscriptions = <String, String>{};

  /// Frames written before the handshake completed.
  final List<StompFrame> _outbox = <StompFrame>[];

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _socketSubscription;
  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;
  Completer<void>? _handshake;

  int _nextSubscriptionId = 0;
  int _reconnectAttempt = 0;
  DateTime _lastReceived = DateTime.now();
  bool _disposed = false;

  ChatConnection _connection = ChatConnection.idle;

  /// Current lifecycle state.
  ChatConnection get connection => _connection;

  bool get isConnected => _connection == ChatConnection.connected;

  /// All events from this socket.
  Stream<ChatEvent> get events => _events.stream;

  /// Opens the socket and completes the STOMP handshake.
  ///
  /// Safe to call repeatedly: a no-op while connecting or connected. Failures are
  /// reported through [events] and a reconnect is scheduled, so callers do not
  /// need to handle the error themselves.
  Future<void> connect() async {
    if (_disposed) throw StateError('ChatSocket has been disposed');
    if (_connection == ChatConnection.connecting || _connection == ChatConnection.connected) {
      return;
    }
    _reconnectTimer?.cancel();
    _setConnection(ChatConnection.connecting);

    final completer = Completer<void>();
    _handshake = completer;

    try {
      final token = await tokenProvider();
      final channel = WebSocketChannel.connect(_endpoint(token));
      _channel = channel;
      _decoder.clear();

      await channel.ready.timeout(handshakeTimeout);
      _socketSubscription = channel.stream.listen(
        _onData,
        onError: (Object error) => _fail('socket error: $error'),
        onDone: () => _fail('socket closed'),
        cancelOnError: false,
      );

      _send(StompFrame(StompCommand.connect, headers: {
        'accept-version': '1.2',
        'heart-beat': '${heartbeat.inMilliseconds},${heartbeat.inMilliseconds}',
        'X-School-Id': schoolId,
        'host': Uri.parse(baseUrl).host,
      }));

      await completer.future.timeout(handshakeTimeout);
    } on Object catch (error) {
      // A transport failure has already moved the socket into `reconnecting`;
      // hitting the timeout is the only case left for us to handle here.
      if (_connection != ChatConnection.reconnecting) _fail('$error');
      return;
    }
  }

  /// Subscribes to [destination], returning the subscription id.
  ///
  /// Subscribing the same destination twice returns the existing id, so a screen
  /// that rebuilds does not accumulate duplicate subscriptions.
  String subscribe(String destination) {
    for (final entry in _subscriptions.entries) {
      if (entry.value == destination) return entry.key;
    }

    final id = 'sub-${_nextSubscriptionId++}';
    _subscriptions[id] = destination;
    if (isConnected) _sendSubscription(id, destination);
    return id;
  }

  /// Removes a subscription by id. Unknown ids are ignored.
  void unsubscribe(String id) {
    if (_subscriptions.remove(id) == null) return;
    if (isConnected) {
      _send(StompFrame(StompCommand.unsubscribe, headers: {'id': id}));
    }
  }

  /// Sends [payload] as JSON to [destination].
  ///
  /// Returns false when the socket is not connected — the caller decides whether
  /// to queue the work for later or fall back to the REST endpoint.
  bool sendJson(String destination, Object payload) {
    if (!isConnected) return false;
    return _send(StompFrame(
      StompCommand.send,
      headers: {
        'destination': destination,
        'content-type': 'application/json',
      },
      body: jsonEncode(payload),
    ));
  }

  /// Closes the socket and stops reconnecting. The instance is dead afterwards.
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _reconnectTimer?.cancel();
    _heartbeatTimer?.cancel();
    if (isConnected) {
      _send(StompFrame(StompCommand.disconnect, headers: const {'receipt': 'bye'}));
    }
    _setConnection(ChatConnection.closed);
    await _socketSubscription?.cancel();
    await _channel?.sink.close();
    _channel = null;
    await _events.close();
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  Uri _endpoint(String? token) {
    final base = Uri.parse(baseUrl);
    return base.replace(
      scheme: base.scheme == 'https' ? 'wss' : 'ws',
      path: path,
      queryParameters: {
        if (token != null && token.isNotEmpty) 'access_token': token,
      },
    );
  }

  void _onData(dynamic raw) {
    _lastReceived = DateTime.now();
    final chunk = switch (raw) {
      String value => value,
      List<int> bytes => utf8.decode(bytes, allowMalformed: true),
      _ => null,
    };
    if (chunk == null) return;

    for (final frame in _decoder.add(chunk)) {
      _dispatch(frame);
    }
  }

  void _dispatch(StompFrame frame) {
    switch (frame.command) {
      case StompServerCommand.connected:
        _onConnected(frame);
      case StompServerCommand.message:
        _onMessage(frame);
      case StompServerCommand.error:
        final code = frame.headers['message'] ?? 'error';
        _emit(ChatErrorEvent(code: code, message: frame.body.isEmpty ? code : frame.body));
      case StompServerCommand.receipt:
        break;
      default:
        // Unknown commands are ignored: forward compatibility over strictness.
        break;
    }
  }

  void _onConnected(StompFrame frame) {
    _reconnectAttempt = 0;
    _lastReceived = DateTime.now();
    _setConnection(ChatConnection.connected);
    _startHeartbeat();

    // Replay subscriptions first, then anything queued before the handshake.
    _subscriptions.forEach(_sendSubscription);
    final queued = List<StompFrame>.of(_outbox);
    _outbox.clear();
    for (final frame in queued) {
      _send(frame);
    }

    final completer = _handshake;
    if (completer != null && !completer.isCompleted) completer.complete();
  }

  void _onMessage(StompFrame frame) {
    final subscriptionId = frame.headers['subscription'];
    final destination = subscriptionId == null ? null : _subscriptions[subscriptionId];

    Map<String, dynamic>? body;
    if (frame.body.isNotEmpty) {
      try {
        final decoded = jsonDecode(frame.body);
        if (decoded is Map<String, dynamic>) body = decoded;
      } on FormatException {
        body = null;
      }
    }
    if (body == null) return;

    if (destination == ChatTopics.ack) {
      _emit(ChatAckEvent(body));
      return;
    }
    if (destination == ChatTopics.errors) {
      _emit(ChatErrorEvent(
        code: '${body['code'] ?? 'error'}',
        message: '${body['message'] ?? body['detail'] ?? ''}',
      ));
      return;
    }

    // The channel topic carries both messages and typing notices.
    if (body.containsKey('senderMembershipId') || body.containsKey('body')) {
      _emit(ChatMessageEvent(body));
    } else if (body.containsKey('displayName') || body.containsKey('membershipId')) {
      _emit(ChatTypingEvent(
        channelId: '${body['channelId'] ?? ''}',
        membershipId: '${body['membershipId'] ?? ''}',
        displayName: body['displayName'] as String?,
      ));
    }
  }

  void _sendSubscription(String id, String destination) {
    _send(StompFrame(StompCommand.subscribe, headers: {
      'id': id,
      'destination': destination,
      'ack': 'auto',
    }));
  }

  bool _send(StompFrame frame) {
    final channel = _channel;
    if (channel == null || _connection != ChatConnection.connected) {
      // CONNECT is the one frame allowed through before CONNECTED; everything
      // else waits in the outbox and is flushed on connect.
      if (frame.command == StompCommand.connect) {
        channel?.sink.add(frame.encode());
        return true;
      }
      _outbox.add(frame);
      return false;
    }
    channel.sink.add(frame.encode());
    return true;
  }

  void _startHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(heartbeat, (_) {
      if (_disposed) return;
      final silentFor = DateTime.now().difference(_lastReceived);
      // Two missed intervals in either direction means the link is dead even
      // though the OS has not told us yet.
      if (silentFor > heartbeat * 2) {
        _fail('heartbeat timeout');
        return;
      }
      _channel?.sink.add('\n');
    });
  }

  void _fail(String reason) {
    final completer = _handshake;
    _handshake = null;
    if (completer != null && !completer.isCompleted) {
      completer.completeError(StateError(reason));
    }
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
    _socketSubscription?.cancel();
    _socketSubscription = null;
    _channel = null;
    if (_disposed || _connection == ChatConnection.closed) return;
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    _reconnectAttempt++;
    _setConnection(ChatConnection.reconnecting, attempt: _reconnectAttempt);

    // 1s, 2s, 4s, 8s, then the cap — enough to ride out a deploy or a dropped
    // mobile connection without hammering the server.
    final delay = Duration(
      milliseconds: (1000 * (1 << (_reconnectAttempt - 1).clamp(0, 4)))
          .clamp(1000, maxReconnectDelay.inMilliseconds),
    );
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(delay, () {
      if (_disposed) return;
      _connection = ChatConnection.idle;
      unawaited(connect());
    });
  }

  void _setConnection(ChatConnection state, {int? attempt}) {
    if (_connection == state && attempt == null) return;
    _connection = state;
    _emit(ChatConnectionChanged(state, attempt: attempt));
  }

  void _emit(ChatEvent event) {
    if (!_events.isClosed) _events.add(event);
  }
}
