import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/chat/chat_socket.dart';
import '../../../core/networking/token_store.dart';
import '../../../core/session/session.dart';
import '../../../core/networking/api_client.dart';

/// Provides a ChatSocket scoped to the currently selected school.
/// Returns null when no school / no session is available.
final chatSocketProvider = Provider<ChatSocket?>((ref) {
  final schoolId = ref.watch(sessionProvider.select((s) => s.membership?.schoolId));
  if (schoolId == null) return null;

  final tokenStore = ref.read(tokenStoreProvider);

  Future<String?> tokenProvider() => tokenStore.accessToken;

  final api = ref.read(apiClientProvider);
  final socket = ChatSocket(
    baseUrl: api._dio.options.baseUrl, // access the ApiClient base url
    schoolId: schoolId,
    tokenProvider: tokenProvider,
  );

  ref.onDispose(() {
    unawaited(socket.dispose());
  });

  return socket;
});
