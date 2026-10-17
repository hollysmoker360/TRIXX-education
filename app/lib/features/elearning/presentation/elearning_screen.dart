import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// E-learning workspace: courses built from existing learning items, plus assignments and resources.
class ELearningScreen extends ConsumerStatefulWidget {
  const ELearningScreen({super.key});
  @override
  ConsumerState<ELearningScreen> createState() => _ELearningScreenState();
}

class _CourseAction {
  const _CourseAction(this.label, this.icon, this.run);
  final String label;
  final IconData icon;
  final Future<bool> Function() run;
}

class _ELearningScreenState extends ConsumerState<ELearningScreen> {
  static const _elearning = '/api/v1/elearning';
  static const _learning = '/api/v1/learning';
  String _section = 'courses';
  bool _busy = false;

  Map<String, String> _sections(S s) => {
        'courses': 'Courses',
        'assignments': 'Assignments',
        'resources': 'Resources',
      };

  bool get _manager => ref.read(sessionProvider).can('learning.manage');

  /// Learners use the "for me" views; staff manage everything in the school.
  String _pathFor(bool manager) => switch (_section) {
        'courses' => manager ? '$_elearning/courses' : '$_elearning/courses/for-me',
        'assignments' => manager ? '$_learning/assignments' : '$_learning/assignments/for-me',
        _ => manager ? '$_learning/resources' : '$_learning/resources/for-me',
      };

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final manager = ref.watch(sessionProvider).can('learning.manage');
    final provider = recordsProvider(_pathFor(manager));
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
              child: _buildList(context, rows, manager),
            ),
          ),
        ),
      ]),
      floatingActionButton: manager && _section == 'courses'
          ? FloatingActionButton.extended(
              onPressed: _busy ? null : () => _run(() => _editCourse()),
              icon: const Icon(Icons.add),
              label: const Text('New course'),
            )
          : null,
    );
  }

  Widget _buildList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    switch (_section) {
      case 'courses':
        return _coursesList(context, rows, manager);
      case 'assignments':
        return _assignmentsList(context, rows, manager);
      default:
        return _resourcesList(context, rows);
    }
  }

  Widget _coursesList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) {
            final status = manager ? '${r['status'] ?? 'DRAFT'}' : _learnerStatus(r);
            return StatusChip(label: status, kind: _statusKind(status));
          },
          width: 130,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Items',
          cell: (ctx, r) => Text('${r['itemCount'] ?? 0}'),
          numeric: true,
          width: 70,
        ),
        if (manager)
          AppColumn<Map<String, dynamic>>(
            label: 'Learners',
            cell: (ctx, r) => Text('${r['enrolledCount'] ?? 0}'),
            numeric: true,
            width: 90,
          ),
        AppColumn<Map<String, dynamic>>(
          label: manager ? 'Avg progress' : 'Progress',
          cell: (ctx, r) => Text('${r['progress'] ?? 0}%'),
          numeric: true,
          width: 110,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      onTap: _busy ? null : (r) => _showActions('${r['title']}', manager ? _staffActions(r) : _learnerActions(r)),
      searchHint: 'Search courses',
      empty: EmptyState(
        icon: Icons.school_outlined,
        title: 'No courses yet',
        message: manager ? 'Create a course to start publishing lessons.' : 'Courses for your class will appear here.',
      ),
    );
  }

  Widget _assignmentsList(BuildContext context, List<Map<String, dynamic>> rows, bool manager) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Due',
          cell: (ctx, r) => Text(_date(r['dueAt'])),
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'State',
          cell: (ctx, r) {
            final state = manager ? '${r['state'] ?? 'draft'}'.toUpperCase() : '${r['submissionState'] ?? 'NOT_SUBMITTED'}'.toUpperCase();
            return StatusChip(label: state, kind: _submissionKind(state));
          },
          width: 140,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search assignments',
      empty: const EmptyState(
        icon: Icons.assignment_outlined,
        title: 'No assignments yet',
        message: 'Assignments appear here once published for your classes.',
      ),
    );
  }

  Widget _resourcesList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Title',
          cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Type',
          cell: (ctx, r) => Text('${r['resourceType'] ?? r['type'] ?? ''}'),
          width: 140,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search resources',
      empty: const EmptyState(
        icon: Icons.folder_open_outlined,
        title: 'No resources yet',
        message: 'Upload learning materials for your classes.',
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  List<_CourseAction> _staffActions(Map<String, dynamic> r) {
    final id = r['id'];
    final status = '${r['status']}';
    return [
      _CourseAction('View items', Icons.list_alt, () => _showItems(r)),
      if (status != 'ARCHIVED') ...[
        _CourseAction('Edit', Icons.edit_outlined, () => _editCourse(r)),
        _CourseAction('Add learning item', Icons.playlist_add, () => _addItem(r)),
      ],
      if (status == 'DRAFT')
        _CourseAction('Publish', Icons.public, () => _post('$_elearning/courses/$id/publish')),
      if (status == 'PUBLISHED') ...[
        _CourseAction('Enroll class', Icons.group_add_outlined, () => _post('$_elearning/courses/$id/enroll', data: {'studentIds': <String>[]})),
        _CourseAction('Learner progress', Icons.insights_outlined, () => _showProgress(r)),
      ],
      if (status != 'ARCHIVED')
        _CourseAction('Archive', Icons.archive_outlined,
            () => _post('$_elearning/courses/$id/archive', confirm: 'Archive ${r['title']}?')),
      if (status == 'DRAFT')
        _CourseAction('Delete', Icons.delete_outline, () async {
          if (!await confirmAction(context, 'Delete course?', 'Delete ${r['title']}? This cannot be undone.')) return false;
          await ref.read(apiClientProvider).delete('$_elearning/courses/$id');
          return true;
        }),
    ];
  }

  List<_CourseAction> _learnerActions(Map<String, dynamic> r) {
    final id = r['id'];
    return [
      if (r['enrolled'] != true)
        _CourseAction('Join course', Icons.login, () => _post('$_elearning/courses/$id/join'))
      else
        _CourseAction('My progress', Icons.checklist, () => _showMyProgress(r)),
    ];
  }

  Future<void> _showActions(String title, List<_CourseAction> actions) async {
    if (actions.isEmpty) return;
    final chosen = await showModalBottomSheet<_CourseAction>(
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
      if (await body() && mounted) ref.invalidate(recordsProvider(_pathFor(_manager)));
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

  Future<bool> _editCourse([Map<String, dynamic>? record]) async {
    final subjects = await ref.read(recordsProvider('/api/v1/academics/subjects').future);
    final classes = await ref.read(recordsProvider('/api/v1/academics/classes').future);
    if (!mounted) return false;
    final api = ref.read(apiClientProvider);
    return showRecordEditor(
      context,
      title: record == null ? 'New course' : 'Edit course',
      initial: {...?record},
      fields: [
        const RecordField('title', 'Title'),
        const RecordField('description', 'Description', kind: FieldKind.multiline, required: false),
        RecordField('subjectId', 'Subject', required: false, options: recordOptions(subjects)),
        RecordField('classGroupId', 'Class (learners)', required: false, options: recordOptions(classes)),
      ],
      onSave: (values) async {
        final body = {for (final key in ['title', 'description', 'subjectId', 'classGroupId']) key: values[key]};
        if (record == null) {
          await api.post('$_elearning/courses', data: body);
        } else {
          await api.patch('$_elearning/courses/${record['id']}', data: body);
        }
      },
    );
  }

  Future<bool> _addItem(Map<String, dynamic> course) async {
    final type = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          for (final entry in const {'RESOURCE': 'Resource', 'ASSIGNMENT': 'Assignment', 'QUIZ': 'Quiz'}.entries)
            ListTile(title: Text(entry.value), onTap: () => Navigator.pop(ctx, entry.key)),
        ]),
      ),
    );
    if (type == null || !mounted) return false;
    final source = switch (type) {
      'RESOURCE' => '$_learning/resources',
      'ASSIGNMENT' => '$_learning/assignments',
      _ => '$_learning/quizzes',
    };
    final rows = await ref.read(recordsProvider(source).future);
    if (!mounted) return false;
    final api = ref.read(apiClientProvider);
    return showRecordEditor(
      context,
      title: 'Add to ${course['title']}',
      initial: const {'required': true},
      fields: [
        RecordField('refId', type[0] + type.substring(1).toLowerCase(),
            options: {for (final r in rows) '${r['id']}': '${r['title'] ?? r['name'] ?? ''}'}),
        const RecordField('required', 'Required for completion', kind: FieldKind.toggle),
      ],
      onSave: (values) => api.post('$_elearning/courses/${course['id']}/items',
          data: {'itemType': type, 'refId': values['refId'], 'required': values['required'] == true}),
    );
  }

  Future<bool> _showItems(Map<String, dynamic> course) async {
    final rows = await ref.read(recordsProvider('$_elearning/courses/${course['id']}/items').future);
    if (!mounted) return false;
    await _listDialog('${course['title']}', rows.isEmpty ? 'No learning items yet.' : null, [
      for (final r in rows)
        ListTile(
          dense: true,
          leading: Icon(_itemIcon('${r['itemType']}')),
          title: Text('${r['title']}'),
          subtitle: Text(r['required'] == true ? 'Required' : 'Optional'),
        ),
    ]);
    return false;
  }

  Future<bool> _showProgress(Map<String, dynamic> course) async {
    final rows = await ref.read(recordsProvider('$_elearning/courses/${course['id']}/progress').future);
    if (!mounted) return false;
    await _listDialog('${course['title']} · progress', rows.isEmpty ? 'No learners enrolled.' : null, [
      for (final r in rows)
        ListTile(
          dense: true,
          title: Text('${r['studentName'] ?? ''}'),
          subtitle: Text(r['averageScore'] == null ? '${r['admissionNumber'] ?? ''}' : 'Average score ${r['averageScore']}%'),
          trailing: Text('${r['progress']}%'),
        ),
    ]);
    return true;
  }

  Future<bool> _showMyProgress(Map<String, dynamic> course) async {
    final data = await ref.read(apiClientProvider).get('$_elearning/courses/${course['id']}/my-progress') as Map<String, dynamic>;
    if (!mounted) return false;
    final items = (data['items'] as List).cast<Map<String, dynamic>>();
    await _listDialog('${data['title']} · ${data['progress']}%', items.isEmpty ? 'No learning items yet.' : null, [
      for (final r in items)
        ListTile(
          dense: true,
          leading: Icon(r['completed'] == true ? Icons.check_circle : Icons.radio_button_unchecked,
              semanticLabel: r['completed'] == true ? 'Completed' : 'Not completed'),
          title: Text('${r['title']}'),
          subtitle: Text(r['required'] == true ? 'Required' : 'Optional'),
          trailing: r['scorePercent'] == null ? null : Text('${r['scorePercent']}%'),
        ),
    ]);
    return true;
  }

  Future<void> _listDialog(String title, String? emptyMessage, List<Widget> tiles) => showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(title),
          content: SizedBox(
            width: 420,
            child: emptyMessage != null ? Text(emptyMessage) : ListView(shrinkWrap: true, children: tiles),
          ),
          actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: Text(S.of(ctx).cancel))],
        ),
      );

  static IconData _itemIcon(String type) => switch (type) {
        'ASSIGNMENT' => Icons.assignment_outlined,
        'QUIZ' => Icons.quiz_outlined,
        _ => Icons.description_outlined,
      };

  static String _learnerStatus(Map<String, dynamic> r) =>
      r['completedAt'] != null ? 'COMPLETED' : r['enrolled'] == true ? 'ENROLLED' : 'AVAILABLE';

  static String _date(Object? value) {
    final parsed = value == null ? null : DateTime.tryParse('$value');
    if (parsed == null) return '${value ?? ''}';
    final local = parsed.toLocal();
    return '${local.year}-${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')}';
  }

  StatusKind _statusKind(String status) {
    switch (status.toUpperCase()) {
      case 'PUBLISHED':
      case 'COMPLETED':
        return StatusKind.success;
      case 'ENROLLED':
        return StatusKind.pending;
      case 'DRAFT':
      case 'ARCHIVED':
        return StatusKind.neutral;
      default:
        return StatusKind.info;
    }
  }

  StatusKind _submissionKind(String state) {
    switch (state) {
      case 'GRADED':
      case 'PUBLISHED':
        return StatusKind.success;
      case 'SUBMITTED':
      case 'RESUBMITTED':
        return StatusKind.pending;
      case 'RETURNED':
        return StatusKind.warning;
      case 'NOT_SUBMITTED':
        return StatusKind.error;
      default:
        return StatusKind.neutral;
    }
  }
}
