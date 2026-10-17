import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/core/chat/chat_socket.dart';
import 'package:schoolsphere_app/core/chat/stomp.dart';

/// The real-time chat client is only as good as its framing, so the STOMP codec
/// is pinned here — including the awkward cases (split frames, heartbeats,
/// several frames per chunk, escaped headers) that a socket delivers in practice.
void main() {
  group('STOMP framing', () {
    test('encodes a frame with the STOMP 1.2 grammar', () {
      final frame = StompFrame('SEND', headers: {
        'destination': '/app/channels/abc/send',
        'content-type': 'application/json',
      }, body: '{"body":"hi"}');

      final encoded = frame.encode();
      expect(encoded, startsWith('SEND\n'));
      expect(encoded, contains('destination:/app/channels/abc/send\n'));
      expect(encoded, contains('content-type:application/json\n'));
      expect(encoded, contains('\n\n{"body":"hi"}'));
      expect(encoded, endsWith('\u0000'));
    });

    test('escapes and restores header values', () {
      const original = 'line:\u0000value';
      final escaped = StompFrame.escapeHeader('a\nb:c\\d');
      expect(escaped, 'a\\nb\\cc\\\\d');
      expect(StompFrame.unescapeHeader(escaped), 'a\nb:c\\d');
      expect(StompFrame.unescapeHeader(StompFrame.escapeHeader(original)), original);
    });

    test('decodes a complete MESSAGE frame with a JSON body', () {
      const raw = 'MESSAGE\n'
          'subscription:sub-0\n'
          'content-type:application/json\n'
          '\n'
          '{"id":"m1","body":"hello"}\u0000';

      final frames = StompDecoder().add(raw);
      expect(frames, hasLength(1));
      expect(frames.single.command, 'MESSAGE');
      expect(frames.single.headers['subscription'], 'sub-0');
      expect(jsonDecode(frames.single.body), {'id': 'm1', 'body': 'hello'});
    });

    test('buffers a frame that arrives in pieces', () {
      final decoder = StompDecoder();
      expect(decoder.add('MESSAGE\nsubscription:sub'), isEmpty);
      expect(decoder.add('-0\n\n{"id":"m1"}'), isEmpty);
      final frames = decoder.add('\u0000');
      expect(frames, hasLength(1));
      expect(frames.single.body, '{"id":"m1"}');
    });

    test('splits several frames out of one chunk', () {
      final frames = StompDecoder().add(
        'CONNECTED\nversion:1.2\n\n\u0000'
        'MESSAGE\nsubscription:sub-1\n\n{"a":1}\u0000',
      );
      expect(frames.map((f) => f.command), ['CONNECTED', 'MESSAGE']);
    });

    test('ignores heartbeats and blank noise', () {
      final frames = StompDecoder().add('\n\n\n');
      expect(frames, isEmpty);
      final mixed = StompDecoder().add('\nCONNECTED\nversion:1.2\n\n\u0000\n');
      expect(mixed, hasLength(1));
      expect(mixed.single.command, 'CONNECTED');
    });

    test('tolerates CRLF line endings', () {
      final frames = StompDecoder().add('RECEIPT\r\nreceipt-id:1\r\n\r\n\u0000');
      expect(frames, hasLength(1));
      expect(frames.single.headers['receipt-id'], '1');
    });

    test('clear() drops a partially buffered frame', () {
      final decoder = StompDecoder()..add('MESSAGE\nsubscription:sub-0\n\n{"id":');
      decoder.clear();
      expect(decoder.add('CONNECTED\nversion:1.2\n\n\u0000').single.command, 'CONNECTED');
    });
  });

  group('chat destinations', () {
    test('match the backend contract', () {
      expect(
        ChatTopics.channel('school-1', 'chan-9'),
        '/topic/school.school-1.channel.chan-9',
      );
      expect(ChatTopics.send('chan-9'), '/app/channels/chan-9/send');
      expect(ChatTopics.typing('chan-9'), '/app/channels/chan-9/typing');
      expect(ChatTopics.ack, '/user/queue/chat.ack');
      expect(ChatTopics.errors, '/user/queue/chat.errors');
    });
  });

  group('ChatSocket bookkeeping', () {
    ChatSocket build() => ChatSocket(
          baseUrl: 'http://localhost:8080',
          schoolId: 'school-1',
          tokenProvider: () async => 'token-1',
        );

    test('starts idle and refuses to send without a connection', () {
      final socket = build();
      addTearDown(socket.dispose);
      expect(socket.connection, ChatConnection.idle);
      expect(socket.isConnected, isFalse);
      expect(socket.sendJson(ChatTopics.send('c1'), {'body': 'hi'}), isFalse);
    });

    test('subscribing twice to one destination reuses the id', () {
      final socket = build();
      addTearDown(socket.dispose);
      final first = socket.subscribe('/topic/one');
      final second = socket.subscribe('/topic/one');
      final other = socket.subscribe('/topic/two');
      expect(second, first);
      expect(other, isNot(first));
    });

    test('unsubscribing is idempotent', () {
      final socket = build();
      addTearDown(socket.dispose);
      final id = socket.subscribe('/topic/one');
      socket.unsubscribe(id);
      expect(() => socket.unsubscribe(id), returnsNormally);
      // A fresh subscription after removal gets a new id.
      expect(socket.subscribe('/topic/one'), isNot(id));
    });

    test('dispose moves to closed and ends the event stream', () async {
      final socket = build();
      final events = <ChatEvent>[];
      final subscription = socket.events.listen(events.add);
      addTearDown(subscription.cancel);

      await socket.dispose();
      expect(socket.connection, ChatConnection.closed);
      expect(events.whereType<ChatConnectionChanged>().last.state, ChatConnection.closed);
      await expectLater(socket.dispose(), completes);
    });
  });
}
