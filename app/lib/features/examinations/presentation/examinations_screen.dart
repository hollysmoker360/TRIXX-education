import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Examinations workspace: assessment authoring and approval, sittings, marking and results.
class ExaminationsScreen extends ConsumerStatefulWidget {
  const ExaminationsScreen({super.key});
  @override
  ConsumerState<ExaminationsScreen> createState() => _ExaminationsScreenState();
}

/// A row action offered in the action sheet; [run] performs it and returns whether data changed.
class _ExamAction {
  const _ExamAction(this.label, this.icon, this.run);
  final String label;
  final IconData icon;
  final Future<bool> Function() run;
}

class _ExaminationsScreenState extends ConsumerState<ExaminationsScreen> {
  static const _base = '/api/v1/examinations';
  String _section = 'assessments';
  bool _busy = false;

  Map<String, String> _sections(S s) => {
        'assessments': 'Assessments',
        'exam-sessions': 'Exam sessions',
        'marking-queue': 'Marking queue',
        'results': 'Results',
      };

  String get _path => '$_base/$_section';

  bool get _canManage => ref.read(sessionProvider).can('exam.manage');
  bool get _canMark => ref.read(sessionProvider).can('exam.mark');
  bool get _canApprove => ref.read(sessionProvider).can('exam.approve');

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final provider = recordsProvider(_path);
    final canCreate = session.can('exam.manage') && (_section == 'assessments' || _section == 'exam-sessions');
    return Scaffold(
      body: Column(children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.all(AppSpacing.s16),
          child: Row(children: [
            for (final section in _sections(s).entries)
              if (section.key != 'marking-queue' || session.can('exam.mark') || session.can('exam.approve'))
                Padding(
                  padding: const EdgeInsets.only(right: AppSpacing.s8),
                  child: ChoiceChip(
                    label: Text(section.value),
                    selected: _section == section.key,
                    onSelected: _busy ? null : (_) => setState(() => _section = section.key),
                  ),
                ),
          ]),
        ),
        if (_busy) const LinearProgressIndicator(),
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
      floatingActionButton: canCreate
          ? FloatingActionButton.extended(
              onPressed: _busy ? null : (_section == 'assessments' ? () => _editAssessment() : _scheduleSession),
              icon: const Icon(Icons.add),
              label: Text(_section == 'assessments' ? 'New assessment' : 'Schedule sitting'),
            )
          : null,
    );
  }

  Widget _buildList(BuildContext context, List<Map<String, dynamic>> rows) {
    switch (_section) {
      case 'assessments':
        return _assessmentsList(context, rows);
      case 'exam-sessions':
        return _sessionsList(context, rows);
      case 'marking-queue':
        return _markingList(context, rows);
      default:
        return _resultsList(context, rows);
    }
  }

  AppColumn<Map<String, dynamic>> _statusColumn(String fallback, {double width = 130}) => AppColumn<Map<String, dynamic>>(
        label: 'Status',
        cell: (ctx, r) => StatusChip(
          label: '${r['status'] ?? fallback}',
          kind: _statusKind('${r['status'] ?? fallback}'),
        ),
        width: width,
      );

  Widget _assessmentsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Type',
          cell: (ctx, r) => Text('${r['assessmentType'] ?? ''}'),
          width: 100,
        ),
        _statusColumn('DRAFT'),
        AppColumn<Map<String, dynamic>>(
          label: 'Marks',
          cell: (ctx, r) => Text(_number(r['totalMarks'])),
          numeric: true,
          width: 80,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Duration',
          cell: (ctx, r) => Text(r['durationMinutes'] == null ? '' : '${r['durationMinutes']} min'),
          numeric: true,
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      onTap: _busy ? null : (r) => _showActions('${r['title']}', _assessmentActions(r)),
      searchHint: 'Search assessments',
      empty: const EmptyState(
        icon: Icons.quiz_outlined,
        title: 'No assessments yet',
        message: 'Create an assessment to start building exams.',
      ),
    );
  }

  Widget _sessionsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        _statusColumn('SCHEDULED'),
        AppColumn<Map<String, dynamic>>(
          label: 'Starts',
          cell: (ctx, r) => Text(_dateTime(r['startsAt'])),
          width: 160,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Venue',
          cell: (ctx, r) => Text('${r['venue'] ?? ''}'),
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Candidates',
          cell: (ctx, r) => Text('${r['candidates'] ?? 0}'),
          numeric: true,
          width: 110,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      onTap: _busy ? null : (r) => _showActions('${r['title']}', _sessionActions(r)),
      searchHint: 'Search exam sessions',
      empty: const EmptyState(
        icon: Icons.event_outlined,
        title: 'No exam sessions yet',
        message: 'Schedule an exam session for eligible candidates.',
      ),
    );
  }

  Widget _markingList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Assessment',
          cell: (ctx, r) => Text('${r['assessmentTitle'] ?? r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Candidate',
          cell: (ctx, r) => Text('${r['candidateName'] ?? r['studentName'] ?? ''}'),
          width: 160,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Seat',
          cell: (ctx, r) => Text('${r['seatNumber'] ?? ''}'),
          width: 70,
        ),
        _statusColumn('PENDING', width: 120),
        AppColumn<Map<String, dynamic>>(
          label: 'Marks',
          cell: (ctx, r) => Text(r['marks'] == null ? '' : '${_number(r['marks'])} / ${_number(r['totalMarks'])}'),
          numeric: true,
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      onTap: _busy ? null : (r) => _showActions('${r['candidateName'] ?? ''}', _scriptActions(r)),
      searchHint: 'Search marking tasks',
      empty: const EmptyState(
        icon: Icons.grading_outlined,
        title: 'No marking tasks',
        message: 'Marking tasks appear here when exams are submitted.',
      ),
    );
  }

  Widget _resultsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Assessment',
          cell: (ctx, r) => Text('${r['assessmentTitle'] ?? r['title'] ?? ''}'),
          primary: true,
        ),
        _statusColumn('PENDING_MARKING', width: 150),
        AppColumn<Map<String, dynamic>>(
          label: 'Marked',
          cell: (ctx, r) => Text('${r['marked'] ?? 0} / ${r['candidates'] ?? 0}'),
          numeric: true,
          width: 90,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Average',
          cell: (ctx, r) => Text(_number(r['average'])),
          numeric: true,
          width: 90,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Pass rate',
          cell: (ctx, r) => Text(r['passRate'] == null ? '' : '${r['passRate']}%'),
          numeric: true,
          width: 100,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      onTap: _busy ? null : (r) => _showActions('${r['assessmentTitle']}', _resultActions(r)),
      searchHint: 'Search results',
      empty: const EmptyState(
        icon: Icons.leaderboard_outlined,
        title: 'No results yet',
        message: 'Results appear once candidates are registered for a sitting.',
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  List<_ExamAction> _assessmentActions(Map<String, dynamic> r) {
    final id = r['id'];
    final status = '${r['status']}';
    return [
      if (_canManage && status == 'DRAFT') ...[
        _ExamAction('Edit', Icons.edit_outlined, () => _editAssessment(r)),
        _ExamAction('Submit for review', Icons.send_outlined, () => _post('$_base/assessments/$id/submit')),
      ],
      if (_canApprove && status == 'IN_REVIEW') ...[
        _ExamAction('Approve', Icons.verified_outlined, () => _post('$_base/assessments/$id/approve')),
        _ExamAction('Return to draft', Icons.undo, () => _post('$_base/assessments/$id/return')),
      ],
      if (_canApprove && status == 'APPROVED')
        _ExamAction('Publish', Icons.public, () => _post('$_base/assessments/$id/publish')),
      if (_canManage && (status == 'APPROVED' || status == 'PUBLISHED'))
        _ExamAction('Schedule sitting', Icons.event_available_outlined, () => _scheduleSession(assessmentId: '$id')),
      if (_canManage && status != 'ARCHIVED' && status != 'DRAFT')
        _ExamAction('Archive', Icons.archive_outlined,
            () => _post('$_base/assessments/$id/archive', confirm: 'Archive ${r['title']}?')),
      if (_canManage && status == 'DRAFT')
        _ExamAction('Delete', Icons.delete_outline, () => _delete('$_base/assessments/$id', '${r['title']}')),
    ];
  }

  List<_ExamAction> _sessionActions(Map<String, dynamic> r) {
    final id = r['id'];
    final status = '${r['status']}';
    return [
      _ExamAction('View candidates', Icons.people_outline, () => _showCandidates('$_base/exam-sessions/$id/candidates', '${r['title']}')),
      if (_canManage && status == 'SCHEDULED') ...[
        _ExamAction('Register class candidates', Icons.group_add_outlined,
            () => _post('$_base/exam-sessions/$id/candidates', data: {'studentIds': <String>[]})),
        _ExamAction('Start sitting', Icons.play_arrow_outlined, () => _post('$_base/exam-sessions/$id/start')),
      ],
      if (_canManage && status == 'IN_PROGRESS')
        _ExamAction('Complete sitting', Icons.stop_circle_outlined, () => _post('$_base/exam-sessions/$id/complete')),
      if (_canManage && (status == 'SCHEDULED' || status == 'IN_PROGRESS'))
        _ExamAction('Cancel sitting', Icons.cancel_outlined,
            () => _post('$_base/exam-sessions/$id/cancel', confirm: 'Cancel ${r['title']}? Candidates will be released.')),
    ];
  }

  List<_ExamAction> _scriptActions(Map<String, dynamic> r) {
    final status = '${r['status']}';
    return [
      if (_canMark && (status == 'PENDING' || status == 'MARKED'))
        _ExamAction(status == 'PENDING' ? 'Enter marks' : 'Change marks', Icons.edit_note, () => _mark(r)),
      if (_canApprove && status == 'MARKED')
        _ExamAction('Moderate', Icons.fact_check_outlined, () => _mark(r, moderate: true)),
    ];
  }

  List<_ExamAction> _resultActions(Map<String, dynamic> r) {
    final id = r['id'];
    return [
      _ExamAction('View candidates', Icons.people_outline,
          () => _showCandidates('$_base/results/$id/candidates', '${r['assessmentTitle']}')),
      if (_canApprove && r['status'] == 'READY')
        _ExamAction('Publish results', Icons.campaign_outlined, () => _post('$_base/results/$id/publish',
            confirm: 'Publish results for ${r['assessmentTitle']}? Marks will be locked.')),
    ];
  }

  Future<void> _showActions(String title, List<_ExamAction> actions) async {
    if (actions.isEmpty) return;
    final chosen = await showModalBottomSheet<_ExamAction>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.s16, 0, AppSpacing.s16, AppSpacing.s8),
            child: Semantics(header: true, child: Text(title, style: Theme.of(ctx).textTheme.titleMedium)),
          ),
          for (final action in actions)
            ListTile(leading: Icon(action.icon), title: Text(action.label), onTap: () => Navigator.pop(ctx, action)),
        ]),
      ),
    );
    if (chosen == null || !mounted) return;
    await _run(chosen.run);
  }

  Future<void> _run(Future<bool> Function() body) async {
    setState(() => _busy = true);
    try {
      if (await body() && mounted) ref.invalidate(recordsProvider(_path));
    } catch (e) {
      if (mounted) showApiError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<bool> _post(String path, {Object? data, String? confirm}) async {
    if (confirm != null && !await confirmAction(context, 'Confirm', confirm)) return false;
    await ref.read(apiClientProvider).post(path, data: data);
    return true;
  }

  Future<bool> _delete(String path, String name) async {
    if (!await confirmAction(context, 'Delete assessment?', 'Delete $name? This cannot be undone.')) return false;
    await ref.read(apiClientProvider).delete(path);
    return true;
  }

  Future<bool> _editAssessment([Map<String, dynamic>? record]) async {
    final subjects = await ref.read(recordsProvider('/api/v1/academics/subjects').future);
    final classes = await ref.read(recordsProvider('/api/v1/academics/classes').future);
    if (!mounted) return false;
    final api = ref.read(apiClientProvider);
    return showRecordEditor(
      context,
      title: record == null ? 'New assessment' : 'Edit assessment',
      initial: {
        ...?record,
        if (record != null) 'totalMarks': _intOrNull(record['totalMarks']),
        if (record != null) 'passMark': _intOrNull(record['passMark']),
      },
      fields: [
        const RecordField('title', 'Title'),
        const RecordField('assessmentType', 'Type',
            options: {'EXAM': 'Exam', 'CAT': 'Continuous assessment test', 'MOCK': 'Mock', 'PRACTICAL': 'Practical'}),
        RecordField('subjectId', 'Subject', required: false, options: recordOptions(subjects)),
        RecordField('classGroupId', 'Class (candidates)', required: false, options: recordOptions(classes)),
        const RecordField('totalMarks', 'Total marks', kind: FieldKind.integer, minimum: 1),
        const RecordField('passMark', 'Pass mark', kind: FieldKind.integer, required: false, minimum: 0),
        const RecordField('durationMinutes', 'Duration (minutes)', kind: FieldKind.integer, required: false, minimum: 1),
        const RecordField('instructions', 'Instructions', kind: FieldKind.multiline, required: false),
      ],
      onSave: (values) async {
        final body = {
          for (final key in ['title', 'assessmentType', 'subjectId', 'classGroupId', 'totalMarks', 'passMark',
            'durationMinutes', 'instructions'])
            key: values[key],
        };
        if (record == null) {
          await api.post('$_base/assessments', data: body);
        } else {
          await api.patch('$_base/assessments/${record['id']}', data: body);
        }
      },
    );
  }

  Future<bool> _scheduleSession({String? assessmentId}) async {
    final assessments = await ref.read(recordsProvider('$_base/assessments').future);
    if (!mounted) return false;
    final schedulable = {
      for (final a in assessments)
        if (a['status'] == 'APPROVED' || a['status'] == 'PUBLISHED') '${a['id']}': '${a['title']}',
    };
    final api = ref.read(apiClientProvider);
    final saved = await showRecordEditor(
      context,
      title: 'Schedule sitting',
      initial: {'assessmentId': assessmentId},
      fields: [
        RecordField('assessmentId', 'Assessment', options: schedulable),
        const RecordField('title', 'Sitting title', required: false),
        const RecordField('date', 'Date', kind: FieldKind.date),
        const RecordField('startTime', 'Start time', kind: FieldKind.time),
        const RecordField('endTime', 'End time', kind: FieldKind.time),
        const RecordField('venue', 'Venue', required: false),
      ],
      onSave: (values) async {
        DateTime at(String time) => DateTime.parse('${values['date']}T$time');
        await api.post('$_base/exam-sessions', data: {
          'assessmentId': values['assessmentId'],
          'title': values['title'],
          'startsAt': at('${values['startTime']}').toUtc().toIso8601String(),
          'endsAt': at('${values['endTime']}').toUtc().toIso8601String(),
          'venue': values['venue'],
        });
      },
    );
    if (saved && mounted) ref.invalidate(recordsProvider('$_base/exam-sessions'));
    return saved;
  }

  Future<bool> _mark(Map<String, dynamic> script, {bool moderate = false}) {
    final api = ref.read(apiClientProvider);
    final total = _number(script['totalMarks']);
    return showRecordEditor(
      context,
      title: '${moderate ? 'Moderate' : 'Mark'} · ${script['candidateName'] ?? ''}',
      initial: {'marks': script['marks'] == null ? null : _number(script['marks']), 'remarks': script['remarks']},
      fields: [
        RecordField('marks', 'Marks (out of $total)', required: false),
        if (!moderate) const RecordField('absent', 'Candidate was absent', kind: FieldKind.toggle),
        const RecordField('remarks', 'Remarks', kind: FieldKind.multiline, required: false),
      ],
      onSave: (values) async {
        final absent = values['absent'] == true;
        final marks = num.tryParse('${values['marks'] ?? ''}');
        await api.post('$_base/marking-queue/${script['id']}/${moderate ? 'moderate' : 'mark'}',
            data: {'marks': marks, 'absent': absent, 'remarks': values['remarks']});
      },
    );
  }

  Future<bool> _showCandidates(String path, String title) async {
    final rows = await ref.read(recordsProvider(path).future);
    if (!mounted) return false;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 420,
          child: rows.isEmpty
              ? const Text('No candidates registered.')
              : ListView(shrinkWrap: true, children: [
                  for (final r in rows)
                    ListTile(
                      dense: true,
                      leading: Text('${r['seatNumber'] ?? ''}'),
                      title: Text('${r['candidateName'] ?? ''}'),
                      subtitle: Text('${r['admissionNumber'] ?? ''}'),
                      trailing: Text(r['marks'] == null ? '${r['status']}' : _number(r['marks'])),
                    ),
                ]),
        ),
        actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: Text(S.of(ctx).cancel))],
      ),
    );
    return false;
  }

  static String _number(Object? value) {
    if (value == null) return '';
    final n = num.tryParse('$value');
    if (n == null) return '$value';
    return n == n.roundToDouble() ? n.toInt().toString() : n.toString();
  }

  static int? _intOrNull(Object? value) => value == null ? null : num.tryParse('$value')?.round();

  static String _dateTime(Object? value) {
    final parsed = value == null ? null : DateTime.tryParse('$value');
    if (parsed == null) return '${value ?? ''}';
    final local = parsed.toLocal();
    String two(int n) => n.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} ${two(local.hour)}:${two(local.minute)}';
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'PUBLISHED':
      case 'APPROVED':
      case 'COMPLETED':
      case 'MODERATED':
      case 'READY':
        return StatusKind.success;
      case 'IN_REVIEW':
      case 'SCHEDULED':
      case 'PENDING':
      case 'PENDING_MARKING':
      case 'IN_PROGRESS':
        return StatusKind.pending;
      case 'CANCELLED':
      case 'ABSENT':
        return StatusKind.warning;
      case 'DRAFT':
      case 'ARCHIVED':
        return StatusKind.neutral;
      default:
        return StatusKind.info;
    }
  }
}
