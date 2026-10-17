import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

class PeopleScreen extends ConsumerStatefulWidget {
  const PeopleScreen({super.key});
  @override
  ConsumerState<PeopleScreen> createState() => _PeopleScreenState();
}

class _PeopleScreenState extends ConsumerState<PeopleScreen> {
  Map<String, String> _sections(S s) => {
        'students': s.students,
        'teachers': s.teachers,
        'parents': s.parents,
        'teacher-assignments': s.teachingAssignments,
      };
  String _section = 'students';
  String _query = '';
  bool _busy = false;
  String get _path => '/api/v1/people/$_section';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final manager = ref.watch(sessionProvider).can('people.manage');
    final provider = recordsProvider(_path);
    return Scaffold(
      body: Column(children: [
        SingleChildScrollView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.all(16),
          child: Row(children: [for (final section in _sections(s).entries) Padding(
            padding: const EdgeInsets.only(right: 8), child: ChoiceChip(label: Text(section.value), selected: _section == section.key,
              onSelected: _busy ? null : (_) => setState(() { _section = section.key; _query = ''; })))])),
        Padding(padding: const EdgeInsets.fromLTRB(16, 0, 16, 12), child: TextField(key: ValueKey(_section),
          decoration: InputDecoration(labelText: s.searchPeople, prefixIcon: const Icon(Icons.search)),
          onChanged: (text) => setState(() => _query = text.trim().toLowerCase()))),
        if (_busy) const LinearProgressIndicator(),
        Expanded(child: AsyncView<List<Map<String, dynamic>>>(value: ref.watch(provider), onRetry: () => ref.invalidate(provider),
          isEmpty: (rows) => rows.isEmpty, builder: (rows) {
            final visible = rows.where((r) => '${r['firstName']} ${r['lastName']} ${r['email']} ${r['admissionNumber']} ${r['teacherName']} ${r['subjectName']}'.toLowerCase().contains(_query)).toList();
            if (visible.isEmpty) return EmptyState(message: s.noMatchingPeople);
            return RefreshIndicator(onRefresh: () => ref.refresh(provider.future),
              child: ListView.builder(padding: const EdgeInsets.fromLTRB(16, 0, 16, 96), itemCount: visible.length,
                physics: const AlwaysScrollableScrollPhysics(), itemBuilder: (context, index) {
                  final row = visible[index];
                  final teaching = _section == 'teacher-assignments';
                  return Card(child: ListTile(
                    leading: Icon(teaching ? Icons.school_outlined : Icons.person_outline),
                    title: Text(teaching ? '${row['teacherName']} · ${row['subjectName']}' : '${row['firstName']} ${row['lastName']}'),
                    subtitle: Text(teaching ? '${row['classGroupName']} · ${row['periodsPerWeek']} periods / week'
                      : '${row['admissionNumber'] ?? row['email'] ?? row['staffNumber'] ?? ''}'),
                    onTap: _busy ? null : _section == 'students' ? () => _student(row) : null,
                    trailing: manager && _section != 'parents' ? IconButton(tooltip: teaching ? 'Remove assignment' : 'Edit profile',
                      icon: Icon(teaching ? Icons.delete_outline : Icons.edit_outlined), onPressed: _busy ? null : () => teaching ? _removeAssignment(row) : _edit(row)) : null,
                  ));
                }));
          })),
      ]),
      floatingActionButton: manager ? FloatingActionButton.extended(onPressed: _busy ? null : () => _edit(),
        icon: const Icon(Icons.person_add_alt), label: Text('${s.add} ${_sections(s)[_section]}')) : null,
    );
  }

  Future<Map<String, String>> _options(String path, {String id = 'id'}) async =>
    recordOptions(await ref.read(recordsProvider(path).future), id: id);

  Future<void> _edit([Map<String, dynamic>? row]) async {
    final section = _section;
    final path = _path;
    final s = S.of(context);
    setState(() => _busy = true);
    try {
      final fields = <RecordField>[];
      if (section == 'teacher-assignments') {
        final options = await Future.wait([
          _options('/api/v1/people/teachers'), _options('/api/v1/academics/classes'),
          _options('/api/v1/academics/subjects'), _options('/api/v1/academics/terms'),
        ]);
        fields.addAll([
          RecordField('teacherId', s.teacher, options: options[0]),
          RecordField('classGroupId', s.classLabel, options: options[1]),
          RecordField('subjectId', s.subject, options: options[2]),
          RecordField('termId', s.fieldTerm, options: options[3]),
          RecordField('periodsPerWeek', s.fieldPeriodsPerWeek, kind: FieldKind.integer, maximum: 40),
        ]);
      } else {
        fields.addAll([RecordField('firstName', s.fieldFirstName), RecordField('lastName', s.fieldLastName)]);
        if (section == 'students') {
          fields.addAll([RecordField('admissionNumber', s.admissionNumber),
            RecordField('otherNames', s.fieldOtherNames, required: false),
            RecordField('dateOfBirth', s.fieldDateOfBirth, kind: FieldKind.date, required: false),
            RecordField('gender', s.fieldGender, required: false, options: {'male': s.fieldMale, 'female': s.fieldFemale, 'other': s.fieldOther})]);
          if (row == null) {
            fields.add(RecordField('userId', s.fieldExistingAccount, required: false,
              options: await _options('/api/v1/schools/current/members', id: 'userId')));
          }
        } else {
          if (row == null) fields.add(RecordField('email', s.email, kind: FieldKind.email));
          fields.add(RecordField('phone', s.fieldPhone, required: false));
          if (section == 'teachers') {
            fields.addAll([RecordField('staffNumber', s.fieldStaffNumber, required: false),
              RecordField('maxWeeklyPeriods', s.fieldMaxWeeklyPeriods, kind: FieldKind.integer, required: false)]);
          }
        }
      }
      if (!mounted) return;
      final api = ref.read(apiClientProvider);
      String? password;
      final saved = await showRecordEditor(context, title: '${row == null ? s.add : s.edit} ${_sections(s)[section]}',
        fields: fields, initial: row ?? {}, onSave: (values) async {
          final response = row == null ? await api.post(path, data: values) : await api.patch('$path/${row['id']}', data: values);
          password = (response as Map<String, dynamic>)['temporaryPassword'] as String?;
        });
      if (!mounted || !saved) return;
      ref.invalidate(recordsProvider(path));
      if (password != null) {
        await showDialog<void>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(s.accountCreated),
            content: SelectableText(
              '${s.sharePasswordIntro}\n\n$password',
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context), child: Text(s.done)),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) showApiError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _student(Map<String, dynamic> row) async {
    final s = S.of(context);
    setState(() => _busy = true);
    try {
      final api = ref.read(apiClientProvider);
      final result = await api.get('/api/v1/people/students/${row['id']}') as Map<String, dynamic>;
      final classes = await _options('/api/v1/academics/classes');
      if (!mounted) return;
      final enrollment = result['enrollment'] as Map<String, dynamic>?;
      final guardians = (result['guardianLinks'] as List).cast<Map<String, dynamic>>();
      final manager = ref.read(sessionProvider).can('people.manage');
      final action = await showDialog<String>(context: context, builder: (context) => AlertDialog(
        scrollable: true, title: Text('${row['firstName']} ${row['lastName']}'),
        content: SizedBox(width: 480, child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('${s.admissionNumber}: ${row['admissionNumber']}'),
          const SizedBox(height: 12),
          Text(enrollment == null ? s.noActiveEnrollment : '${classes[enrollment['classGroupId']] ?? s.classLabel} · ${enrollment['status']}'),
          const Divider(),
          Text(s.guardians),
          if (guardians.isEmpty) Text(s.noGuardiansLinked),
          for (final guardian in guardians) ListTile(contentPadding: EdgeInsets.zero,
            title: Text('${guardian['parentName']}'), subtitle: Text('${guardian['relationship']}')),
          if (manager) Wrap(spacing: 8, children: [
            TextButton(onPressed: () => Navigator.pop(context, 'enroll'), child: Text(s.enroll)),
            TextButton(onPressed: () => Navigator.pop(context, 'guardian'), child: Text(s.linkGuardian)),
            if (enrollment != null) TextButton(onPressed: () => Navigator.pop(context, 'transition'), child: Text(s.changeEnrollment)),
          ]),
        ])), actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text(s.close))],
      ));
      if (!mounted || action == null) return;
      final fields = <RecordField>[];
      String path;
      if (action == 'enroll') {
        path = '/api/v1/people/enrollments';
        fields.addAll([
          RecordField('academicYearId', s.fieldAcademicYear, options: await _options('/api/v1/academics/years')),
          RecordField('classGroupId', s.classLabel, options: classes),
          RecordField('status', s.status, options: {'applied': 'Applied', 'admitted': 'Admitted', 'enrolled': 'Enrolled'}),
        ]);
      } else if (action == 'guardian') {
        path = '/api/v1/people/guardians';
        fields.addAll([
          RecordField('parentId', s.fieldParentGuardian, options: await _options('/api/v1/people/parents')),
          RecordField('relationship', s.relation),
          RecordField('primary', s.fieldPrimaryGuardian, kind: FieldKind.toggle),
          RecordField('attendance', s.fieldMayViewAttendance, kind: FieldKind.toggle),
          RecordField('grades', s.fieldMayViewGrades, kind: FieldKind.toggle),
        ]);
      } else {
        path = '/api/v1/people/enrollments/${enrollment!['id']}/transition';
        const transitions = {'applied': ['admitted', 'withdrawn'], 'admitted': ['enrolled', 'withdrawn'], 'enrolled': ['transferred', 'withdrawn', 'graduated']};
        fields.addAll([
          RecordField('toStatus', s.fieldNewStatus, options: {for (final status in transitions[enrollment['status']] ?? <String>[]) status: status}),
          RecordField('reason', s.reason, kind: FieldKind.multiline),
        ]);
      }
      if (!mounted) return;
      final saved = await showRecordEditor(context, title: action == 'guardian' ? s.linkGuardian : s.enrollmentLabel,
        fields: fields, initial: action == 'enroll' ? {'status': 'enrolled'} : {}, onSave: (values) async {
          if (action != 'transition') values['studentId'] = row['id'];
          if (action == 'guardian') values['visibilities'] = {'attendance': values.remove('attendance') == true, 'grades': values.remove('grades') == true};
          await api.post(path, data: values);
        });
      if (saved && mounted) {
        ref.invalidate(recordsProvider('/api/v1/people/students'));
        showApiError(context, 'Saved successfully.');
      }
    } catch (e) {
      if (mounted) showApiError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _removeAssignment(Map<String, dynamic> row) async {
    if (!await confirmAction(context, 'Remove teaching assignment?', 'Remove ${row['subjectName']} for ${row['teacherName']}?')) return;
    if (!mounted) return;
    try {
      await ref.read(apiClientProvider).delete('/api/v1/people/teacher-assignments/${row['id']}');
      if (mounted) ref.invalidate(recordsProvider('/api/v1/people/teacher-assignments'));
    } catch (e) {
      if (mounted) showApiError(context, e);
    }
  }
}
