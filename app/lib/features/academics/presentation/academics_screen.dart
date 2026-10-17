import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

class AcademicsScreen extends ConsumerStatefulWidget {
  const AcademicsScreen({super.key});
  @override
  ConsumerState<AcademicsScreen> createState() => _AcademicsScreenState();
}

class _AcademicsScreenState extends ConsumerState<AcademicsScreen> {
  Map<String, String> _sections(S s) => {
        'years': s.academicYears,
        'terms': s.terms,
        'departments': s.departments,
        'classes': s.classes,
        'streams': s.streams,
        'subjects': s.subjects,
        'periods': s.periods,
        'classrooms': s.classrooms,
      };
  String _section = 'years';
  bool _busy = false;
  String get _path => '/api/v1/academics/$_section';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final manager = ref.watch(sessionProvider).can('academics.manage');
    final provider = recordsProvider(_path);
    return Scaffold(
      body: Column(children: [
        SingleChildScrollView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.all(16),
          child: Row(children: [for (final section in _sections(s).entries) Padding(
            padding: const EdgeInsets.only(right: 8), child: ChoiceChip(label: Text(section.value),
              selected: _section == section.key, onSelected: _busy ? null : (_) => setState(() => _section = section.key)))])),
        if (_busy) const LinearProgressIndicator(),
        Expanded(child: AsyncView<List<Map<String, dynamic>>>(value: ref.watch(provider),
          onRetry: () => ref.invalidate(provider), isEmpty: (rows) => rows.isEmpty,
          builder: (rows) => RefreshIndicator(onRefresh: () => ref.refresh(provider.future),
            child: ListView.builder(padding: const EdgeInsets.fromLTRB(16, 0, 16, 96),
              physics: const AlwaysScrollableScrollPhysics(), itemCount: rows.length,
              itemBuilder: (context, index) {
                final row = rows[index];
                return Card(child: ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.school_outlined)),
                  title: Text('${row['name']}'), subtitle: Text(_description(row)),
                  trailing: manager ? PopupMenuButton<String>(enabled: !_busy, tooltip: s.manageLabel('${row['name']}'),
                    onSelected: (action) => action == 'edit' ? _edit(row) : _action(action, row),
                    itemBuilder: (_) => [
                      if (['years', 'departments', 'classes', 'subjects'].contains(_section))
                        PopupMenuItem(value: 'edit', child: Text(S.of(context).edit)),
                      if (['years', 'terms'].contains(_section) && row['active'] != true && row['current'] != true)
                        PopupMenuItem(value: 'activate', child: Text(S.of(context).setCurrent)),
                      PopupMenuItem(value: 'delete', child: Text(S.of(context).delete)),
                    ]) : null,
                ));
              })))),
      ]),
      floatingActionButton: manager ? FloatingActionButton.extended(onPressed: _busy ? null : () => _edit(),
        icon: const Icon(Icons.add), label: Text('${s.add} ${_sections(s)[_section]}')) : null,
    );
  }

  String _description(Map<String, dynamic> row) => [
    if (row['startsOn'] != null) '${row['startsOn']} — ${row['endsOn']}',
    if (row['startsAt'] != null) 'Period ${row['seq']} · ${row['startsAt']} — ${row['endsAt']}',
    if (row['code'] != null) '${row['code']}',
    if (row['level'] != null) S.of(context).levelValue('${row['level']}'),
    if (row['capacity'] != null) S.of(context).seatsLabel('${row['capacity']}'),
    if (row['roomType'] != null) '${row['roomType']}',
    if (row['active'] == true || row['current'] == true) S.of(context).current,
    if (row['core'] == true) S.of(context).fieldCoreSubject,
    if (row['isBreak'] == true) S.of(context).breakLabel,
  ].join(' · ');

  Future<void> _edit([Map<String, dynamic>? record]) async {
    final path = _path;
    final section = _section;
    final s = S.of(context);
    setState(() => _busy = true);
    try {
      final fields = <RecordField>[RecordField('name', s.fieldName)];
      if (section == 'terms' || section == 'periods') {
        final years = await ref.read(recordsProvider('/api/v1/academics/years').future);
        fields.add(RecordField('academicYearId', s.fieldAcademicYear, options: recordOptions(years)));
        fields.add(RecordField('seq', s.fieldSequence, kind: FieldKind.integer));
      }
      if (section == 'years' || section == 'terms') {
        fields.addAll([RecordField('startsOn', s.fieldStartDate, kind: FieldKind.date),
          RecordField('endsOn', s.fieldEndDate, kind: FieldKind.date)]);
        if (record == null) fields.add(RecordField(section == 'years' ? 'active' : 'current', s.setCurrent, kind: FieldKind.toggle));
      }
      if (['departments', 'subjects'].contains(section)) fields.add(RecordField('code', s.fieldCode, required: false));
      if (['classes', 'subjects'].contains(section)) {
        final departments = await ref.read(recordsProvider('/api/v1/academics/departments').future);
        fields.add(RecordField('departmentId', s.fieldDepartment, required: false, options: recordOptions(departments)));
      }
      if (section == 'classes') fields.add(RecordField('level', s.fieldGradeLevel, kind: FieldKind.integer, required: false));
      if (['classes', 'classrooms'].contains(section)) fields.add(RecordField('capacity', s.fieldCapacity, kind: FieldKind.integer, required: false));
      if (section == 'classrooms') fields.add(RecordField('roomType', s.fieldRoomType, required: false));
      if (section == 'subjects') fields.add(RecordField('core', s.fieldCoreSubject, kind: FieldKind.toggle));
      if (section == 'streams') {
        final classes = await ref.read(recordsProvider('/api/v1/academics/classes').future);
        fields.add(RecordField('classGroupId', s.classLabel, options: recordOptions(classes)));
      }
      if (section == 'periods') {
        fields.addAll([
          RecordField('startsAt', s.fieldStartTime, kind: FieldKind.time),
          RecordField('endsAt', s.fieldEndTime, kind: FieldKind.time),
          RecordField('isBreak', s.fieldBreakPeriod, kind: FieldKind.toggle),
        ]);
      }
      if (!mounted) return;
      final api = ref.read(apiClientProvider);
      final saved = await showRecordEditor(context, title: '${record == null ? s.add : s.edit} ${_sections(s)[section]}',
        fields: fields, initial: record ?? {}, onSave: (values) async {
          if (record == null) { await api.post(path, data: values); }
          else { await api.patch('$path/${record['id']}', data: values); }
        });
      if (saved && mounted) ref.invalidate(recordsProvider(path));
    } catch (e) {
      if (mounted) showApiError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _action(String action, Map<String, dynamic> row) async {
    final path = _path;
    final confirmed = await confirmAction(
      context,
      action == 'delete' ? 'Delete record?' : 'Set current?',
      action == 'delete'
          ? 'Delete ${row['name']}? Records still in use cannot be removed.'
          : 'Make ${row['name']} current?',
    );
    if (!confirmed) return;
    if (!mounted) return;
    setState(() => _busy = true);
    try {
      final api = ref.read(apiClientProvider);
      if (action == 'delete') { await api.delete('$path/${row['id']}'); }
      else { await api.post('$path/${row['id']}/activate'); }
      if (mounted) ref.invalidate(recordsProvider(path));
    } catch (e) {
      if (mounted) showApiError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}
