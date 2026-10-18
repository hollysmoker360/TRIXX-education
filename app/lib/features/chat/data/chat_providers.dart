import 'dart:async' show unawaited;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/chat/chat_socket.dart';
import '../../../core/config/app_config.dart';
import '../../../core/session/session.dart';

/// Provides a ChatSocket scoped to the currently selected school.
/// Returns null when no school / no session is available.
final chatSocketProvider = Provider<ChatSocket?>((ref) {
  final schoolId = ref.watch(sessionProvider.select((s) => s.membership?.schoolId));
  if (schoolId == null) return null;

  final tokenStore = ref.read(tokenStoreProvider);
  Future<String?> tokenProvider() => tokenStore.accessToken;

  final socket = ChatSocket(
    baseUrl: AppConfig.apiBaseUrl,
    schoolId: schoolId,
    tokenProvider: tokenProvider,
  );

  ref.onDispose(() {
    unawaited(socket.dispose());
  });

  return socket;
});
