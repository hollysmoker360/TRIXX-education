import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _forMeProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/learning/resources/for-me');
  return (data as List).cast<Map<String, dynamic>>();
});

final _manageProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref
      .watch(apiClientProvider)
      .get('/api/v1/learning/resources', query: {'size': 50}) as Map<String, dynamic>;
  return (data['items'] as List? ?? const []).cast<Map<String, dynamic>>();
});

class ResourcesScreen extends ConsumerWidget {
  const ResourcesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isManager = ref.watch(sessionProvider).can('learning.manage');
    final provider = isManager ? _manageProvider : _forMeProvider;

    return AsyncView<List<Map<String, dynamic>>>(
      value: ref.watch(provider),
      onRetry: () => ref.invalidate(provider),
      isEmpty: (rows) => rows.isEmpty,
      builder: (rows) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(provider),
        child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: rows.length,
          itemBuilder: (context, i) {
            final resource = rows[i];
            final bookmarked = resource['bookmarked'] == true;
            final completed = resource['completed'] == true;
            return Card(
              child: ListTile(
                leading: Icon(_icon('${resource['resourceType']}')),
                title: Text('${resource['title']}'),
                subtitle: Text([
                  '${resource['resourceType']}',
                  if ((resource['topic'] ?? '').toString().isNotEmpty)
                    '${resource['topic']}',
                  if (isManager) '${resource['state']}',
                ].join(' · ')),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (completed)
                      const Padding(
                        padding: EdgeInsets.only(right: 6),
                        child: Icon(Icons.check_circle, color: Colors.green),
                      ),
                    if (!isManager)
                      IconButton(
                        tooltip: S.of(context).bookmark,
                        icon: Icon(
                            bookmarked ? Icons.bookmark : Icons.bookmark_border),
                        onPressed: () => _bookmark(
                            context, ref, resource, !bookmarked),
                      ),
                  ],
                ),
                onTap: () => _open(context, ref, resource, isManager),
              ),
            );
          },
        ),
      ),
    );
  }

  static IconData _icon(String type) {
    switch (type) {
      case 'pdf':
      case 'document':
        return Icons.description_outlined;
      case 'video':
        return Icons.play_circle_outline;
      case 'audio':
        return Icons.graphic_eq;
      case 'link':
        return Icons.link;
      default:
        return Icons.menu_book_outlined;
    }
  }

  Future<void> _bookmark(
    BuildContext context,
    WidgetRef ref,
    Map<String, dynamic> resource,
    bool bookmarked,
  ) async {
    final s = S.of(context);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(apiClientProvider)
          .post('/api/v1/learning/resources/${resource['id']}/bookmark');
      ref.invalidate(_forMeProvider);
      messenger.showSnackBar(
          SnackBar(content: Text('${s.save} — ${bookmarked ? 'on' : 'off'}')));
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  Future<void> _open(
    BuildContext context,
    WidgetRef ref,
    Map<String, dynamic> resource,
    bool isManager,
  ) async {
    final s = S.of(context);
    final version = resource['latestVersion'] as Map<String, dynamic>?;
    final externalUrl = version?['externalUrl'];
    final textBody = '${version?['textBody'] ?? ''}';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: MediaQuery.of(sheetContext).viewInsets.bottom + 20,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('${resource['title']}',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 6),
              Text('${resource['description'] ?? ''}'),
              const SizedBox(height: 12),
              if (externalUrl != null && '$externalUrl'.isNotEmpty)
                FilledButton.tonalIcon(
                  onPressed: () async {
                    final uri = Uri.tryParse('$externalUrl');
                    if (uri != null) {
                      await launchUrl(uri, webOnlyWindowName: '_blank');
                    }
                  },
                  icon: const Icon(Icons.open_in_new),
                  label: Text(s.openResource),
                ),
              if (textBody.isNotEmpty) ...[
                const SizedBox(height: 12),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(textBody),
                  ),
                ),
              ],
              const SizedBox(height: 12),
              if (!isManager)
                FilledButton(
                  onPressed: () async {
                    final messenger = ScaffoldMessenger.of(context);
                    try {
                      await ref.read(apiClientProvider).post(
                          '/api/v1/learning/resources/${resource['id']}/complete');
                      messenger.showSnackBar(SnackBar(content: Text(s.done)));
                      if (sheetContext.mounted) Navigator.pop(sheetContext);
                      ref.invalidate(_forMeProvider);
                    } on ApiProblem catch (e) {
                      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
                    }
                  },
                  child: Text(s.done),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
