import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/session/session.dart';

/// Recent notifications for the signed-in user.
final notificationsProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/notifications', query: {'limit': 50});
  return (data as List).cast<Map<String, dynamic>>();
});

/// Unread count for the top-bar badge.
final unreadCountProvider = FutureProvider<int>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/notifications/unread-count') as Map<String, dynamic>;
  return (data['unread'] as num?)?.toInt() ?? 0;
});

/// Synchronous view of the badge count so the shell never flashes a spinner.
final unreadNotificationsProvider = Provider<int>(
  (ref) => ref.watch(unreadCountProvider).maybeWhen(data: (value) => value, orElse: () => 0),
);

Future<void> markNotificationRead(WidgetRef ref, String id) async {
  final api = ref.read(apiClientProvider);
  await api.post('/api/v1/notifications/$id/read');
  ref.invalidate(unreadCountProvider);
  ref.invalidate(notificationsProvider);
}

Future<void> markAllNotificationsRead(WidgetRef ref) async {
  final api = ref.read(apiClientProvider);
  await api.post('/api/v1/notifications/read-all');
  ref.invalidate(unreadCountProvider);
  ref.invalidate(notificationsProvider);
}
