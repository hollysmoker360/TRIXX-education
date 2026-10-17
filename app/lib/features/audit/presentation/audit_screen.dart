import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// Paginated audit trail reads (specification section 6.11). Append-only: no
/// edit or delete affordances exist because the backend exposes none.
class AuditScreen extends ConsumerStatefulWidget {
  const AuditScreen({super.key, this.embedded = false});

  final bool embedded;

  @override
  ConsumerState<AuditScreen> createState() => _AuditScreenState();
}

class _AuditScreenState extends ConsumerState<AuditScreen> {
  int _page = 0;
  static const _size = 25;
  String _action = '';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    if (!session.can('audit.view')) {
      return ScreenScaffold(
        title: s.audit,
        embedded: widget.embedded,
        child: const ForbiddenView(),
      );
    }
    final provider = _auditProvider((page: _page, action: _action));
    final result = ref.watch(provider);

    return ScreenScaffold(
      title: s.audit,
      description: s.referenceLabel,
      embedded: widget.embedded,
      child: AsyncView<Map<String, dynamic>>(
        value: result,
        onRetry: () => ref.invalidate(provider),
        builder: (data) {
          final items = (data['items'] as List?)?.cast<Map<String, dynamic>>() ?? const [];
          final total = (data['total'] as num?)?.toInt() ?? items.length;
          if (items.isEmpty) {
            return EmptyState(message: s.emptyList, icon: Icons.history_outlined);
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              FilterBar(
                search: AppTextField(
                  label: s.search,
                  hint: 'action or entity',
                  prefixIcon: Icons.search,
                  onSubmitted: (value) => setState(() {
                    _action = value;
                    _page = 0;
                  }),
                ),
                filters: [
                  if (_action.isNotEmpty)
                    InputChip(
                      label: Text(_action),
                      onDeleted: () => setState(() {
                        _action = '';
                        _page = 0;
                      }),
                    ),
                ],
                onClearAll: () => setState(() {
                  _action = '';
                  _page = 0;
                }),
              ),
              Card(
                child: Column(
                  children: [
                    for (final entry in items)
                      ListTile(
                        dense: true,
                        leading: Icon(_iconFor('${entry['action']}')),
                        title: Text('${entry['action']} · ${entry['entity'] ?? ''}'),
                        subtitle: Text(
                          [
                            if (entry['actorUserId'] != null) 'actor ${_short('${entry['actorUserId']}')}',
                            if (entry['entityId'] != null) 'record ${_short('${entry['entityId']}')}',
                            '${entry['createdAt'] ?? ''}',
                          ].join(' · '),
                          style: context.texts.bodySmall,
                        ),
                        onTap: () => _showDetail(context, entry),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.s12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('${_page * _size + 1}–${_page * _size + items.length} / $total',
                      style: context.texts.bodySmall),
                  Row(
                    children: [
                      AppButton(
                        label: s.previous,
                        variant: AppButtonVariant.text,
                        onPressed: _page == 0 ? null : () => setState(() => _page -= 1),
                      ),
                      AppButton(
                        label: s.next,
                        variant: AppButtonVariant.text,
                        onPressed: _page * _size + items.length >= total
                            ? null
                            : () => setState(() => _page += 1),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          );
        },
      ),
    );
  }

  void _showDetail(BuildContext context, Map<String, dynamic> entry) {
    showSidePanel<void>(
      context,
      title: '${entry['action']}',
      subtitle: '${entry['entity'] ?? ''}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _row(context, 'Reference', '${entry['id']}'),
          _row(context, 'Actor', '${entry['actorUserId'] ?? '—'}'),
          _row(context, 'Entity', '${entry['entity'] ?? '—'}'),
          _row(context, 'Record', '${entry['entityId'] ?? '—'}'),
          _row(context, 'Device', '${entry['deviceId'] ?? '—'}'),
          _row(context, 'IP address', '${entry['ipAddress'] ?? '—'}'),
          _row(context, S.of(context).when, '${entry['createdAt'] ?? '—'}'),
          if (entry['beforeState'] != null) ...[
            const SizedBox(height: AppSpacing.s12),
            Text(S.of(context).before, style: context.texts.labelMedium),
            _code(context, '${entry['beforeState']}'),
          ],
          if (entry['afterState'] != null) ...[
            const SizedBox(height: AppSpacing.s12),
            Text(S.of(context).after, style: context.texts.labelMedium),
            _code(context, '${entry['afterState']}'),
          ],
        ],
      ),
    );
  }

  Widget _row(BuildContext context, String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 120,
              child: Text(label, style: context.texts.labelMedium?.copyWith(color: context.tokens.textSecondary)),
            ),
            Expanded(child: SelectableText(value, style: context.texts.bodyMedium)),
          ],
        ),
      );

  Widget _code(BuildContext context, String value) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.s8),
        decoration: BoxDecoration(color: context.tokens.canvas, borderRadius: AppRadius.smAll),
        child: SelectableText(
          value,
          style: context.texts.bodySmall?.copyWith(fontFamily: 'monospace'),
        ),
      );

  static IconData _iconFor(String action) {
    if (action.contains('delete') || action.contains('remove')) return Icons.delete_outline;
    if (action.contains('create') || action.contains('add')) return Icons.add_circle_outline;
    if (action.contains('publish')) return Icons.publish_outlined;
    if (action.contains('login') || action.contains('auth')) return Icons.login_outlined;
    return Icons.edit_note;
  }

  static String _short(String id) => id.length <= 8 ? id : id.substring(0, 8);
}

final _auditProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, ({int page, String action})>((ref, key) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/audit', query: {
    'page': key.page,
    'size': 25,
    if (key.action.isNotEmpty) 'action': key.action,
  });
  return data as Map<String, dynamic>;
});
