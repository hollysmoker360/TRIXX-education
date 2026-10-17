/// STOMP 1.2 framing.
///
/// The backend exposes chat over STOMP-on-WebSocket (`ChatWebSocketConfig`) with
/// a simple broker, heartbeats, and user queues. Only the subset actually used is
/// implemented here: CONNECT / CONNECTED, SUBSCRIBE / UNSUBSCRIBE, SEND,
/// MESSAGE, RECEIPT, ERROR, and heartbeats. That is deliberately less code than a
/// general STOMP client, and it is fully unit-testable without a socket.
///
/// Frame grammar (STOMP 1.2):
///
/// ```
/// COMMAND\n
/// header:value\n
/// \n
/// body
/// \0
/// ```
///
/// A lone end-of-line outside a frame is a heartbeat.
library;

/// A single STOMP frame.
class StompFrame {
  StompFrame(this.command, {this.headers = const {}, this.body = ''});

  final String command;
  final Map<String, String> headers;
  final String body;

  static const String nullByte = '\u0000';

  /// Serialises the frame, escaping header values as STOMP 1.2 requires.
  String encode() {
    final buffer = StringBuffer(command)..write('\n');
    headers.forEach((key, value) {
      buffer
        ..write(key)
        ..write(':')
        ..write(escapeHeader(value))
        ..write('\n');
    });
    buffer.write('\n');
    if (body.isNotEmpty) buffer.write(body);
    buffer.write(nullByte);
    return buffer.toString();
  }

  /// `\n`, `\r`, `:` and `\` inside a header value must be escaped.
  static String escapeHeader(String value) => value
      .replaceAll('\\', '\\\\')
      .replaceAll('\r', '\\r')
      .replaceAll('\n', '\\n')
      .replaceAll(':', '\\c');

  static String unescapeHeader(String value) {
    final out = StringBuffer();
    for (var i = 0; i < value.length; i++) {
      final char = value[i];
      if (char != '\\' || i + 1 >= value.length) {
        out.write(char);
        continue;
      }
      final next = value[++i];
      out.write(switch (next) {
        'n' => '\n',
        'r' => '\r',
        'c' => ':',
        '\\' => '\\',
        _ => next,
      });
    }
    return out.toString();
  }

  @override
  String toString() => 'StompFrame($command, $headers)';
}

/// Incremental decoder: sockets deliver partial frames, and bodies are binary-safe
/// in principle, so the decoder buffers until it sees the terminating NUL.
class StompDecoder {
  final StringBuffer _buffer = StringBuffer();

  /// Drops any partially buffered frame. Called when a socket is replaced so a
  /// half-delivered frame from the dead connection cannot corrupt the next one.
  void clear() {
    _buffer.clear();
  }

  /// Feeds [chunk] and returns every frame completed by it.
  List<StompFrame> add(String chunk) {
    _buffer.write(chunk);
    var pending = _buffer.toString();
    final frames = <StompFrame>[];
    var terminator = pending.indexOf(StompFrame.nullByte);
    while (terminator >= 0) {
      final segment = pending.substring(0, terminator);
      pending = pending.substring(terminator + 1);
      final frame = parse(segment);
      if (frame != null) frames.add(frame);
      terminator = pending.indexOf(StompFrame.nullByte);
    }
    _buffer
      ..clear()
      ..write(pending);
    return frames;
  }

  /// Parses one NUL-stripped segment. Returns null for heartbeats and blanks.
  static StompFrame? parse(String segment) {
    // Heartbeats and keep-alive newlines before the next command.
    final text = segment.replaceFirst(RegExp(r'^[\r\n]+'), '');
    if (text.trim().isEmpty) return null;

    var headerEnd = text.indexOf('\n\n');
    var separator = 2;
    final crlfEnd = text.indexOf('\r\n\r\n');
    if (headerEnd < 0 || (crlfEnd >= 0 && crlfEnd < headerEnd)) {
      headerEnd = crlfEnd;
      separator = 4;
    }
    if (headerEnd < 0) {
      // Headers only (RECEIPT without a body is still terminated by a blank line,
      // but a malformed frame may arrive without one).
      headerEnd = text.length;
      separator = 0;
    }

    final headerBlock = text.substring(0, headerEnd);
    final body = separator == 0 ? '' : text.substring(headerEnd + separator);
    final lines = headerBlock.split(RegExp(r'\r?\n'));
    final command = lines.first.trim();
    if (command.isEmpty) return null;

    final headers = <String, String>{};
    for (final line in lines.skip(1)) {
      if (line.isEmpty) continue;
      final colon = line.indexOf(':');
      if (colon <= 0) continue;
      headers[line.substring(0, colon)] = StompFrame.unescapeHeader(line.substring(colon + 1));
    }
    return StompFrame(command, headers: headers, body: body);
  }
}

/// STOMP commands the client sends.
abstract final class StompCommand {
  static const connect = 'CONNECT';
  static const subscribe = 'SUBSCRIBE';
  static const unsubscribe = 'UNSUBSCRIBE';
  static const send = 'SEND';
  static const disconnect = 'DISCONNECT';
}

/// STOMP commands the server sends.
abstract final class StompServerCommand {
  static const connected = 'CONNECTED';
  static const message = 'MESSAGE';
  static const receipt = 'RECEIPT';
  static const error = 'ERROR';
}
