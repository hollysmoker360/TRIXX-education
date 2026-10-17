import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Communication & notifications workspace (spec: Communication and notifications).
class CommunicationScreen extends ConsumerStatefulWidget {
  const CommunicationScreen({super.key});
  @override
  ConsumerState<CommunicationScreen> createState() => _CommunicationScreenState();
}

class _CommunicationScreenState extends ConsumerState<CommunicationScreen> {
  String _section = 'inbox';

  Map<String, String> _sections(S s) => {
        'inbox': 'Inbox',
        'announcements': 'Announcements',
        'notifications': 'Notifications',
        'meetings': 'Meetings',
      };

  String get _path => '/api/v1/communication/$_section';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final provider = recordsProvider(_path);
    return Scaffold(
      body: Column(children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.all(AppSpacing.s16),
          child: Row(children: [
            for (final section in _sections(s).entries)
              Padding(
                padding: const EdgeInsets.only(right: AppSpacing.s8),
                child: ChoiceChip(
                  label: Text(section.value),
                  selected: _section == section.key,
                  onSelected: (_) => setState(() => _section = section.key),
                ),
              ),
          ]),
        ),
        Expanded(
          child: AsyncView<List<Map<String, dynamic>>>(
            value: ref.watch(provider),
            onRetry: () => ref.invalidate(provider),
            isEmpty: (rows) => rows.isEmpty,
            emptyMessage: 'No records yet.',
            builder: (rows) => RefreshIndicator(
              onRefresh: () => ref.refresh(provider.future),
              child: _buildList(context, rows),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _buildList(BuildContext context, List<Map<String, dynamic>> rows) {
    switch (_section) {
      case 'inbox':
        return _inboxList(context, rows);
      case 'announcements':
        return _announcementsList(context, rows);
      case 'notifications':
        return _notificationsList(context, rows);
      default:
        return _meetingsList(context, rows);
    }
  }

  Widget _inboxList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Conversation',
          cell: (ctx, r) => Text('${r['title'] ?? r['subject'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Unread',
          cell: (ctx, r) => Text('${r['unread'] ?? 0}'),
          numeric: true,
          width: 80,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search conversations',
      empty: const EmptyState(
        icon: Icons.forum_outlined,
        title: 'No conversations yet',
        message: 'Start a conversation with staff or parents.',
      ),
    );
  }

  Widget _announcementsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Audience',
          cell: (ctx, r) => Text('${r['audience'] ?? ''}'),
          width: 140,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'DRAFT'}',
            kind: _statusKind('${r['status'] ?? 'DRAFT'}'),
          ),
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search announcements',
      empty: const EmptyState(
        icon: Icons.campaign_outlined,
        title: 'No announcements yet',
        message: 'Compose an announcement to reach your audience.',
      ),
    );
  }

  Widget _notificationsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return ListView.builder(
      padding: const EdgeInsets.all(AppSpacing.s16),
      itemCount: rows.length,
      itemBuilder: (context, index) {
        final row = rows[index];
        final read = row['read'] == true || row['readAt'] != null;
        return Card(
          child: ListTile(
            leading: Icon(read ? Icons.notifications_none : Icons.notifications_active,
                color: read ? null : context.tokens.warning),
            title: Text('${row['title'] ?? ''}'),
            subtitle: Text('${row['body'] ?? row['message'] ?? ''}'),
          ),
        );
      },
    );
  }

  Widget _meetingsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'PENDING'}',
            kind: _statusKind('${r['status'] ?? 'PENDING'}'),
          ),
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search meetings',
      empty: const EmptyState(
        icon: Icons.event_outlined,
        title: 'No meeting requests',
        message: 'Meeting requests appear here.',
      ),
    );
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'PUBLISHED':
      case 'SENT':
      case 'CONFIRMED':
        return StatusKind.success;
      case 'PENDING':
      case 'SCHEDULED':
        return StatusKind.pending;
      case 'DRAFT':
        return StatusKind.neutral;
      case 'FAILED':
        return StatusKind.error;
      default:
        return StatusKind.info;
    }
  }
}
