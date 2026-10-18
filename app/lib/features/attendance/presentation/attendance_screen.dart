import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format.dart';
import '../../../core/ids.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/sync/offline_queue.dart';
import '../../../core/widgets/common.dart';

final _classesProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/academics/classes');
  return (data as List).cast<Map<String, dynamic>>();
});

final _sessionsProvider = FutureProvider.autoDispose.family((ref, String date) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/attendance/sessions', query: {'date': date});
  return (data as List).cast<Map<String, dynamic>>();
});

class AttendanceScreen extends ConsumerStatefulWidget {
  const AttendanceScreen({super.key});

  @override
  ConsumerState<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends ConsumerState<AttendanceScreen> {
  DateTime _date = DateTime.now();

  String get _dateKey => '${_date.year.toString().padLeft(4, '0')}-'
      '${_date.month.toString().padLeft(2, '0')}-'
      '${_date.day.toString().padLeft(2, '0')}';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final sessions = ref.watch(_sessionsProvider(_dateKey));
    final classes = ref.watch(_classesProvider);
    final classNames = <String, String>{
      for (final c in classes.valueOrNull ?? const <Map<String, dynamic>>[])
        '${c['id']}': '${c['name']}',
    };

    return Scaffold(
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.chevron_left),
                  onPressed: () => setState(
                      () => _date = _date.subtract(const Duration(days: 1))),
                ),
                Expanded(
                  child: Text(
                    '${relativeDay(_date)} · ${formatDate(_date)}',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.chevron_right),
                  onPressed: () =>
                      setState(() => _date = _date.add(const Duration(days: 1))),
                ),
              ],
            ),
          ),
          Expanded(
            child: AsyncView<List<Map<String, dynamic>>>(
              value: sessions,
              onRetry: () => ref.invalidate(_sessionsProvider(_dateKey)),
              isEmpty: (rows) => rows.isEmpty,
              builder: (rows) => RefreshIndicator(
                onRefresh: () async =>
                    ref.invalidate(_sessionsProvider(_dateKey)),
                child: ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: rows.length,
                  itemBuilder: (context, i) {
                    final row = rows[i];
                    final open = '${row['status']}' == 'open';
                    return Card(
                      child: ListTile(
                        leading: Icon(open ? Icons.lock_open : Icons.lock_outline,
                            color: open ? Colors.orange.shade700 : Colors.green.shade700),
                        title: Text(
                          '${row['sessionType']} · ${classNames['${row['classGroupId']}'] ?? ''}',
                        ),
                        subtitle: Text([
                          if (row['periodSeq'] != null) 'Period ${row['periodSeq']}',
                          if ((row['notes'] ?? '').toString().isNotEmpty) '${row['notes']}',
                        ].join(' · ')),
                        trailing: StatusPill(
                          label: '${row['status']}',
                          color: open ? Colors.orange.shade700 : Colors.green.shade700,
                        ),
                        onTap: () => context.push(AppRoutes.attendanceRegisterLocation('${row['id']}')),
                      ),
                    );
                  },
                ),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openRegister(context, s),
        icon: const Icon(Icons.add),
        label: Text(s.openSession),
      ),
    );
  }

  Future<void> _openRegister(BuildContext context, S s) async {
    final classes = ref.read(_classesProvider).valueOrNull ?? const [];
    if (classes.isEmpty) {
      showApiError(context, 'No classes available yet.');
      return;
    }
    final formKey = GlobalKey<_NewSessionFormState>();
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.openSession),
        content: _NewSessionForm(key: formKey, classes: classes),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(s.cancel),
          ),
          FilledButton(
            onPressed: () async {
              final form = formKey.currentState;
              if (form == null || form.classGroupId == null) return;
              final api = ref.read(apiClientProvider);
              try {
                await api.post('/api/v1/attendance/sessions', data: {
                  'classGroupId': form.classGroupId,
                  'sessionType': 'lesson',
                  'sessionDate': _dateKey,
                  if (form.notes.text.trim().isNotEmpty)
                    'notes': form.notes.text.trim(),
                });
                if (context.mounted) Navigator.pop(context, true);
              } on ApiProblem catch (e) {
                if (context.mounted) showApiError(context, e);
              }
            },
            child: Text(s.create),
          ),
        ],
      ),
    );
    if (created == true) ref.invalidate(_sessionsProvider(_dateKey));
  }
}

class _NewSessionForm extends StatefulWidget {
  const _NewSessionForm({super.key, required this.classes});
  final List<Map<String, dynamic>> classes;

  @override
  State<_NewSessionForm> createState() => _NewSessionFormState();
}

class _NewSessionFormState extends State<_NewSessionForm> {
  String? classGroupId;
  final notes = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        DropdownButtonFormField<String>(
          initialValue: classGroupId,
          decoration: InputDecoration(labelText: S.of(context).classLabel),
          items: [
            for (final c in widget.classes)
              DropdownMenuItem(value: '${c['id']}', child: Text('${c['name']}')),
          ],
          onChanged: (v) => setState(() => classGroupId = v),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: notes,
          decoration: InputDecoration(labelText: S.of(context).notesLabel),
        ),
      ],
    );
  }
}

/// Full register for one session: roster with status marks, offline-capable.
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key, required this.sessionId});
  final String sessionId;

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  Map<String, dynamic>? _detail;
  Object? _error;
  final Map<String, String> _marks = {};
  final Map<String, String> _reasons = {};
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = ref.read(apiClientProvider);
    setState(() => _error = null);
    try {
      final detail = await api.get('/api/v1/attendance/sessions/${widget.sessionId}')
          as Map<String, dynamic>;
      setState(() {
        _detail = detail;
        _marks.clear();
        _reasons.clear();
      });
    } on ApiProblem catch (e) {
      setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: Text(s.attendance)),
        body: ErrorView(error: _error!, onRetry: _load),
      );
    }
    final detail = _detail;
    if (detail == null) {
      return Scaffold(
        appBar: AppBar(title: Text(s.attendance)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    final session = detail['session'] as Map<String, dynamic>;
    final roster = (detail['roster'] as List? ?? const [])
        .cast<Map<String, dynamic>>();
    final counts = detail['counts'] as Map<String, dynamic>? ?? const {};
    final open = '${session['status']}' == 'open';

    return Scaffold(
      appBar: AppBar(
        title: Text('${session['sessionType']} · ${session['sessionDate']}'),
        actions: [
          if (open)
            TextButton(
              onPressed: _busy ? null : _close,
              child: Text(s.closeSession),
            ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Wrap(
              spacing: 8,
              children: [
                StatusPill(label: '${s.markPresent} ${counts['present'] ?? 0}'),
                StatusPill(
                    label: '${s.markAbsent} ${counts['absent'] ?? 0}',
                    color: Colors.red.shade700),
                StatusPill(
                    label: '${s.markLate} ${counts['late'] ?? 0}',
                    color: Colors.orange.shade700),
                StatusPill(
                    label: '${s.markExcused} ${counts['excused'] ?? 0}',
                    color: Colors.blueGrey),
                StatusPill(
                    label: s.unmarkedCount(counts['unmarked'] ?? 0),
                    color: Theme.of(context).colorScheme.outline),
              ],
            ),
          ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: roster.length,
              itemBuilder: (context, i) {
                final row = roster[i];
                final studentId = '${row['studentId']}';
                final status = _marks[studentId] ?? '${row['status'] ?? ''}';
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text('${row['studentName']}'),
                  subtitle: Text('${row['admissionNumber']}'),
                  trailing: _StatusPicker(
                    value: status.isEmpty ? null : status,
                    enabled: open,
                    onChanged: (v) => _pick(studentId, v),
                  ),
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: open
          ? FloatingActionButton.extended(
              onPressed: _busy || _marks.isEmpty ? null : _saveMarks,
              icon: const Icon(Icons.save_outlined),
              label: Text(s.save),
            )
          : null,
    );
  }

  Future<void> _pick(String studentId, String status) async {
    final s = S.of(context);
    final reason = await showDialog<String?>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(status == 'present' ? s.markPresent : status),
        children: [
          if (status != 'present')
            SimpleDialogOption(
              onPressed: () => Navigator.pop(context, ''),
              child: Text(s.noReason),
            ),
          SimpleDialogOption(
            onPressed: () => Navigator.pop(context, status == 'absent' ? 'Sick' : ''),
            child: Text(status == 'absent' ? 'Sick' : 'Continue'),
          ),
        ],
      ),
    );
    setState(() {
      _marks[studentId] = status;
      if (reason != null) _reasons[studentId] = reason;
    });
  }

  Future<void> _saveMarks() async {
    final s = S.of(context);
    final api = ref.read(apiClientProvider);
    final queue = ref.read(offlineQueueProvider);
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _busy = true);
    final payload = {
      'marks': [
        for (final entry in _marks.entries)
          {
            'studentId': entry.key,
            'status': entry.value,
            if ((_reasons[entry.key] ?? '').isNotEmpty)
              'reason': _reasons[entry.key],
          },
      ],
    };
    try {
      await api.post('/api/v1/attendance/sessions/${widget.sessionId}/marks',
          data: payload);
      messenger.showSnackBar(SnackBar(content: Text(s.done)));
      setState(() => _marks.clear());
      await _load();
    } on ApiProblem catch (e) {
      if (e.isOffline) {
        final now = DateTime.now().toUtc().toIso8601String();
        for (final entry in _marks.entries) {
          queue.enqueue(PendingOp(
            opId: newUuid(),
            entity: 'attendance_event',
            entityId: newUuid(),
            type: 'create',
            fields: {
              'sessionId': widget.sessionId,
              'studentId': entry.key,
              'status': entry.value,
              if ((_reasons[entry.key] ?? '').isNotEmpty)
                'reason': _reasons[entry.key],
              'eventTime': now,
            },
          ));
        }
        messenger.showSnackBar(SnackBar(content: Text(s.offlineQueued)));
        setState(() => _marks.clear());
      } else {
        messenger.showSnackBar(SnackBar(content: Text(e.detail)));
      }
    } finally {
      setState(() => _busy = false);
    }
  }

  Future<void> _close() async {
    final s = S.of(context);
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.closeSession),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(s.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(s.done),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _busy = true);
    try {
      await api.post('/api/v1/attendance/sessions/${widget.sessionId}/close',
          data: {'notes': null});
      await _load();
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    } finally {
      setState(() => _busy = false);
    }
  }
}

class _StatusPicker extends StatelessWidget {
  const _StatusPicker({
    required this.value,
    required this.enabled,
    required this.onChanged,
  });

  final String? value;
  final bool enabled;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return SegmentedButton<String>(
      showSelectedIcon: false,
      segments: [
        ButtonSegment(
            value: 'present',
            icon: const Icon(Icons.check, size: 16),
            tooltip: s.markPresent),
        ButtonSegment(
            value: 'absent',
            icon: const Icon(Icons.close, size: 16),
            tooltip: s.markAbsent),
        ButtonSegment(
            value: 'late',
            icon: const Icon(Icons.schedule, size: 16),
            tooltip: s.markLate),
        ButtonSegment(
            value: 'excused',
            icon: const Icon(Icons.event_busy, size: 16),
            tooltip: s.markExcused),
      ],
      selected: value == null ? const {} : {value!},
      emptySelectionAllowed: true,
      onSelectionChanged:
          enabled ? (selection) => onChanged(selection.first) : null,
    );
  }
}
