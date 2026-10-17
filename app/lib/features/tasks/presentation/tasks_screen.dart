import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../core/ids.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/sync/offline_queue.dart';
import '../../../core/widgets/common.dart';

final _tasksProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/tasks');
  return (data as List).cast<Map<String, dynamic>>();
});

class TasksScreen extends ConsumerStatefulWidget {
  const TasksScreen({super.key});

  @override
  ConsumerState<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends ConsumerState<TasksScreen> {
  String _filter = 'open';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final tasks = ref.watch(_tasksProvider);
    final queue = ref.watch(offlineQueueProvider);

    return Scaffold(
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Row(
              children: [
                for (final filter in const ['open', 'done', 'all']) ...[
                  ChoiceChip(
                    label: Text(filter),
                    selected: _filter == filter,
                    onSelected: (_) => setState(() => _filter = filter),
                  ),
                  const SizedBox(width: 8),
                ],
              ],
            ),
          ),
          Expanded(
            child: AsyncView<List<Map<String, dynamic>>>(
              value: tasks,
              onRetry: () => ref.invalidate(_tasksProvider),
              builder: (rows) {
                return StreamBuilder<void>(
                  stream: queue.changes,
                  builder: (context, _) {
                    final queuedCreates = queue
                        .pending()
                        .where((op) => op.entity == 'task' && op.type == 'create')
                        .toList();
                    final visible = rows.where((t) {
                      final done = t['doneAt'] != null;
                      if (_filter == 'open') return !done;
                      if (_filter == 'done') return done;
                      return true;
                    }).toList();
                    if (visible.isEmpty && queuedCreates.isEmpty) {
                      return RefreshIndicator(
                        onRefresh: () async => ref.invalidate(_tasksProvider),
                        child: ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: [EmptyState(message: s.emptyList)],
                        ),
                      );
                    }
                    return RefreshIndicator(
                      onRefresh: () async => ref.invalidate(_tasksProvider),
                      child: ListView(
                        padding: const EdgeInsets.all(16),
                        children: [
                          for (final op in queuedCreates)
                            Card(
                              color: Theme.of(context)
                                  .colorScheme
                                  .tertiaryContainer
                                  .withValues(alpha: 0.4),
                              child: ListTile(
                                leading: const Icon(Icons.cloud_upload_outlined),
                                title: Text('${op.fields['title']}'),
                                subtitle: Text(s.offlineQueued),
                              ),
                            ),
                          for (final task in visible)
                            _TaskTile(
                              task: task,
                              onChanged: () => ref.invalidate(_tasksProvider),
                            ),
                        ],
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context, s),
        icon: const Icon(Icons.add),
        label: Text(s.create),
      ),
    );
  }

  Future<void> _create(BuildContext context, S s) async {
    final formKey = GlobalKey<_TaskFormState>();
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.create),
        content: SizedBox(width: 380, child: _TaskForm(key: formKey)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(s.cancel),
          ),
          FilledButton(
            onPressed: () async {
              final form = formKey.currentState;
              if (form == null || form.title.text.trim().isEmpty) return;
              final api = ref.read(apiClientProvider);
                            final queue = ref.read(offlineQueueProvider);
              final messenger = ScaffoldMessenger.of(context);
              final payload = {
                'title': form.title.text.trim(),
                'description': form.description.text.trim().isEmpty
                    ? null
                    : form.description.text.trim(),
                'dueAt': form.dueAt?.toUtc().toIso8601String(),
                'priority': form.priority,
              };
              try {
                await api.post('/api/v1/tasks', data: payload);
                messenger.showSnackBar(SnackBar(content: Text(s.done)));
                if (context.mounted) Navigator.pop(context, true);
              } on ApiProblem catch (e) {
                if (e.isOffline) {
                                    queue.enqueue(PendingOp(
                    opId: newUuid(),
                    entity: 'task',
                    entityId: newUuid(),
                    type: 'create',
                    fields: payload,
                  ));
                  messenger.showSnackBar(SnackBar(content: Text(s.offlineQueued)));
                  if (context.mounted) Navigator.pop(context, false);
                } else {
                  messenger.showSnackBar(SnackBar(content: Text(e.detail)));
                }
              }
            },
            child: Text(s.create),
          ),
        ],
      ),
    );
    if (created == true) ref.invalidate(_tasksProvider);
  }
}

class _TaskForm extends StatefulWidget {
  const _TaskForm({super.key});

  @override
  State<_TaskForm> createState() => _TaskFormState();
}

class _TaskFormState extends State<_TaskForm> {
  final title = TextEditingController();
  final description = TextEditingController();
  String priority = 'medium';
  DateTime? dueAt;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: title,
            decoration: InputDecoration(labelText: S.of(context).titleLabel),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: description,
            minLines: 1,
            maxLines: 3,
            decoration: InputDecoration(labelText: S.of(context).descriptionLabel),
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: priority,
            decoration: InputDecoration(labelText: S.of(context).priority),
            items: [
              DropdownMenuItem(value: 'low', child: Text(S.of(context).priorityLow)),
              DropdownMenuItem(value: 'medium', child: Text(S.of(context).priorityMedium)),
              DropdownMenuItem(value: 'high', child: Text(S.of(context).priorityHigh)),
            ],
            onChanged: (v) => setState(() => priority = v ?? 'medium'),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: Text(dueAt == null ? S.of(context).noDueDate : formatDate(dueAt!)),
              ),
              TextButton(
                onPressed: () async {
                  final now = DateTime.now();
                  final date = await showDatePicker(
                    context: context,
                    initialDate: dueAt ?? now,
                    firstDate: now.subtract(const Duration(days: 30)),
                    lastDate: now.add(const Duration(days: 365)),
                  );
                  if (date != null) setState(() => dueAt = date);
                },
                child: Text(S.of(context).pickDate),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TaskTile extends ConsumerWidget {
  const _TaskTile({required this.task, required this.onChanged});
  final Map<String, dynamic> task;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final done = task['doneAt'] != null;
    final due = parseInstant(task['dueAt']);
    final overdue = !done && due != null && due.isBefore(DateTime.now());
    return Card(
      child: ListTile(
        leading: Checkbox(
          value: done,
          onChanged: done ? null : (_) => _complete(context, ref),
        ),
        title: Text(
          '${task['title']}',
          style: done
              ? TextStyle(
                  decoration: TextDecoration.lineThrough,
                  color: Theme.of(context).colorScheme.outline,
                )
              : null,
        ),
        subtitle: Text([
          '${task['priority']}',
          if (due != null) '${overdue ? 'Overdue' : 'Due'} ${formatDateTime(due)}',
          if ((task['description'] ?? '').toString().isNotEmpty)
            '${task['description']}',
        ].join(' · ')),
        trailing: IconButton(
          tooltip: S.of(context).delete,
          icon: const Icon(Icons.delete_outline),
          onPressed: () => _delete(context, ref, s),
        ),
      ),
    );
  }

  Future<void> _complete(BuildContext context, WidgetRef ref) async {
    final s = S.of(context);
    final api = ref.read(apiClientProvider);
        final queue = ref.read(offlineQueueProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await api.post('/api/v1/tasks/${task['id']}/complete');
      onChanged();
    } on ApiProblem catch (e) {
      if (e.isOffline) {
                queue.enqueue(PendingOp(
          opId: newUuid(),
          entity: 'task',
          entityId: '${task['id']}',
          type: 'update',
          fields: {'done': true},
        ));
        messenger.showSnackBar(SnackBar(content: Text(s.offlineQueued)));
      } else {
        messenger.showSnackBar(SnackBar(content: Text(e.detail)));
      }
    }
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, S s) async {
    final api = ref.read(apiClientProvider);
        final queue = ref.read(offlineQueueProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await api.delete('/api/v1/tasks/${task['id']}');
      messenger.showSnackBar(SnackBar(content: Text(s.done)));
      onChanged();
    } on ApiProblem catch (e) {
      if (e.isOffline) {
                queue.enqueue(PendingOp(
          opId: newUuid(),
          entity: 'task',
          entityId: '${task['id']}',
          type: 'delete',
        ));
        messenger.showSnackBar(SnackBar(content: Text(s.offlineQueued)));
      } else {
        messenger.showSnackBar(SnackBar(content: Text(e.detail)));
      }
    }
  }
}
