import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/sync/offline_queue.dart';
import '../../../core/widgets/common.dart';

final _sessionsTodayProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final now = DateTime.now();
  final date = '${now.year.toString().padLeft(4, '0')}-'
      '${now.month.toString().padLeft(2, '0')}-'
      '${now.day.toString().padLeft(2, '0')}';
  final data = await api.get('/api/v1/attendance/sessions', query: {'date': date});
  return (data as List).cast<Map<String, dynamic>>();
});

final _forMeAssignmentsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments/for-me');
  return (data as List).cast<Map<String, dynamic>>();
});

final _myChildrenProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/people/me/children');
  return (data as List).cast<Map<String, dynamic>>();
});

final _channelsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/chat/channels');
  return (data as List).cast<Map<String, dynamic>>();
});

class OverviewScreen extends ConsumerWidget {
  const OverviewScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final theme = Theme.of(context);
    final session = ref.watch(sessionProvider);
    final membership = session.membership;

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(_sessionsTodayProvider);
        ref.invalidate(_forMeAssignmentsProvider);
        ref.invalidate(_myChildrenProvider);
        ref.invalidate(_channelsProvider);
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 26,
                child: Text(
                  _initials(session.user?.displayName ?? '?'),
                  style: theme.textTheme.titleMedium,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _greeting(session.user?.displayName ?? ''),
                      style: theme.textTheme.titleLarge,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Flexible(child: Text(membership?.schoolName ?? s.noSchool)),
                        const SizedBox(width: 8),
                        if (membership != null) StatusPill(label: membership.roleLabel),
                      ],
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: s.syncNow,
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  await ref.read(syncServiceProvider).syncNow();
                  messenger.showSnackBar(SnackBar(content: Text(s.syncNow)));
                },
                icon: const Icon(Icons.sync, size: 18),
              ),
            ],
          ),
          const SizedBox(height: 16),
          const _PendingOpsBanner(),
          if (session.can('attendance.take')) ...[
            const SizedBox(height: 8),
            const _TeacherToday(),
          ],
          if (session.can('assignment.submit')) ...[
            const SizedBox(height: 8),
            const _StudentDue(),
          ],
          if (session.can('child.data.view')) ...[
            const SizedBox(height: 8),
            const _ParentChildren(),
          ],
          if (session.can('chat.use')) ...[
            const SizedBox(height: 8),
            const _UnreadMessages(),
          ],
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  static String _greeting(String name) {
    final hour = DateTime.now().hour;
    final part = hour < 12 ? 'Good morning' : (hour < 17 ? 'Good afternoon' : 'Good evening');
    return '$part, ${name.split(' ').first}';
  }

  static String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty || parts.first.isEmpty) return '?';
    return parts.take(2).map((p) => p[0].toUpperCase()).join();
  }
}

class _PendingOpsBanner extends ConsumerWidget {
  const _PendingOpsBanner();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final queue = ref.watch(offlineQueueProvider);
    return StreamBuilder<void>(
      stream: queue.changes,
      builder: (context, _) {
        final pending = queue.pending().length;
        if (pending == 0) return const SizedBox.shrink();
        final s = S.of(context);
        return Card(
          color: Theme.of(context).colorScheme.tertiaryContainer,
          child: ListTile(
            leading: const Icon(Icons.cloud_upload_outlined),
            title: Text('$pending ${s.pendingChanges}'),
            subtitle: Text(s.offlineQueued),
            trailing: TextButton(
              onPressed: () => ref.read(syncServiceProvider).syncNow(),
              child: Text(s.syncNow),
            ),
          ),
        );
      },
    );
  }
}

class _TeacherToday extends ConsumerWidget {
  const _TeacherToday();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final sessions = ref.watch(_sessionsTodayProvider);
    return SectionCard(
      title: '${s.today} — ${s.attendance}',
      child: sessions.when(
        loading: () => const LinearProgressIndicator(),
        error: (e, _) => Text('$e'),
        data: (rows) {
          if (rows.isEmpty) return Text(s.emptyList, style: Theme.of(context).textTheme.bodyMedium);
          return Column(
            children: [
              for (final row in rows)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(row['status'] == 'open' ? Icons.lock_open : Icons.lock_outline),
                  title: Text('${row['sessionType']} · ${row['sessionDate']}'),
                  subtitle: Text('${row['notes'] ?? ''}'),
                  trailing: StatusPill(
                    label: '${row['status']}',
                    color: row['status'] == 'open'
                        ? Colors.orange.shade700
                        : Colors.green.shade700,
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _StudentDue extends ConsumerWidget {
  const _StudentDue();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final assignments = ref.watch(_forMeAssignmentsProvider);
    return SectionCard(
      title: '${s.assignments} — ${s.dueSoon}',
      child: assignments.when(
        loading: () => const LinearProgressIndicator(),
        error: (e, _) => Text('$e'),
        data: (rows) {
          final pending = rows
              .where((r) => (r['submissionState'] ?? 'pending') != 'submitted' &&
                  (r['submissionState'] ?? '') != 'graded')
              .take(4)
              .toList();
          if (pending.isEmpty) return Text(s.emptyList, style: Theme.of(context).textTheme.bodyMedium);
          return Column(
            children: [
              for (final a in pending)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.assignment_outlined),
                  title: Text('${a['title']}'),
                  subtitle: Text('Due ${a['dueAt']?.toString().substring(0, 16) ?? ''}'),
                  trailing: StatusPill(
                    label: '${a['submissionState'] ?? 'pending'}',
                    color: Colors.blueGrey,
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _ParentChildren extends ConsumerWidget {
  const _ParentChildren();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final children = ref.watch(_myChildrenProvider);
    return SectionCard(
      title: S.of(context).myChildren,
      child: children.when(
        loading: () => const LinearProgressIndicator(),
        error: (e, _) => Text('$e'),
        data: (rows) {
          if (rows.isEmpty) return Text(S.of(context).emptyList);
          return Column(
            children: [
              for (final child in rows)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.face_outlined),
                  title: Text('${child['firstName']} ${child['lastName']}'),
                  subtitle: Text('${child['admissionNumber']} · ${child['relationship']}'),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _UnreadMessages extends ConsumerWidget {
  const _UnreadMessages();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final channels = ref.watch(_channelsProvider);
    return SectionCard(
      title: S.of(context).messages,
      child: channels.when(
        loading: () => const LinearProgressIndicator(),
        error: (e, _) => Text('$e'),
        data: (rows) {
          final unread = <Map<String, dynamic>>[];
          for (final row in rows) {
            final count = (row['unreadCount'] as num?)?.toInt() ?? 0;
            if (count > 0) unread.add(row);
          }
          if (unread.isEmpty) {
            return Text(S.of(context).allCaughtUp, style: Theme.of(context).textTheme.bodyMedium);
          }
          return Column(
            children: [
              for (final row in unread.take(4))
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.forum_outlined),
                  title: Text('${row['name']}'),
                  subtitle: Text(
                    '${(row['lastMessage'] as Map?)?['senderName'] ?? ''}: '
                    '${(row['lastMessage'] as Map?)?['body'] ?? ''}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: CircleAvatar(
                    radius: 12,
                    child: Text('${row['unreadCount']}', style: const TextStyle(fontSize: 10)),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}
