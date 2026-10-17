import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import 'notifications_provider.dart';

/// Notification centre (specification section 6.9). Grouped by type, with a
/// clear "something new" preview that never contains grades or balances.
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key, this.embedded = false});

  final bool embedded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final notifications = ref.watch(notificationsProvider);

    return ScreenScaffold(
      title: s.notifications,
      description: s.notifyPreviewNote,
      embedded: embedded,
      primaryAction: AppButton(
        label: s.markAllRead,
        variant: AppButtonVariant.outline,
        icon: Icons.done_all,
        onPressed: () => markAllNotificationsRead(ref),
      ),
      child: AsyncView<List<Map<String, dynamic>>>(
        value: notifications,
        onRetry: () => ref.invalidate(notificationsProvider),
        isEmpty: (rows) => rows.isEmpty,
        emptyMessage: s.noNotifications,
        emptyAction: null,
        builder: (rows) {
          final grouped = <String, List<Map<String, dynamic>>>{};
          for (final row in rows) {
            grouped.putIfAbsent('${row['type'] ?? 'general'}', () => []).add(row);
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final entry in grouped.entries) ...[
                Padding(
                  padding: const EdgeInsets.only(top: AppSpacing.s8, bottom: AppSpacing.s8),
                  child: Text(
                    _label(entry.key),
                    style: context.texts.labelMedium?.copyWith(color: context.tokens.textSecondary),
                  ),
                ),
                Card(
                  child: Column(
                    children: [
                      for (final item in entry.value)
                        _NotificationTile(
                          item: item,
                          onRead: () => markNotificationRead(ref, '${item['id']}'),
                        ),
                    ],
                  ),
                ),
              ],
            ],
          );
        },
      ),
    );
  }

  static String _label(String type) {
    if (type.isEmpty) return 'General';
    return type.replaceAll('_', ' ').replaceFirstMapped(
          RegExp(r'^.'), (m) => m.group(0)!.toUpperCase(),
        );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.item, required this.onRead});

  final Map<String, dynamic> item;
  final VoidCallback onRead;

  @override
  Widget build(BuildContext context) {
    final unread = item['readAt'] == null;
    return ListTile(
      leading: Icon(
        unread ? Icons.mark_email_unread_outlined : Icons.mark_email_read_outlined,
        color: unread ? context.scheme.primary : context.tokens.textSecondary,
      ),
      title: Text(
        '${item['title'] ?? ''}',
        style: unread ? context.texts.titleSmall : context.texts.bodyMedium,
      ),
      subtitle: Text(
        '${item['body'] ?? ''}',
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Text(
        _relative(context, '${item['createdAt'] ?? ''}'),
        style: context.texts.labelSmall,
      ),
      onTap: unread ? onRead : null,
    );
  }

  String _relative(BuildContext context, String iso) {
    final at = DateTime.tryParse(iso)?.toLocal();
    if (at == null) return '';
    final diff = DateTime.now().difference(at);
    final s = S.of(context);
    if (diff.inMinutes < 1) return s.justNow;
    if (diff.inMinutes < 60) return '${diff.inMinutes}${s.minutesAgo}';
    if (diff.inHours < 24) return '${diff.inHours}${s.hoursAgo}';
    return '${diff.inDays}${s.daysAgo}';
  }
}
