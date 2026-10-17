import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _forMeProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments/for-me');
  return (data as List).cast<Map<String, dynamic>>();
});

final _manageProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments',
      query: {'size': 50}) as Map<String, dynamic>;
  return (data['items'] as List? ?? const []).cast<Map<String, dynamic>>();
});

final _subjectsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/academics/subjects');
  return (data as List).cast<Map<String, dynamic>>();
});

final _classesProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/academics/classes');
  return (data as List).cast<Map<String, dynamic>>();
});

class AssignmentsScreen extends ConsumerWidget {
  const AssignmentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    if (session.can('assignment.create')) {
      return const _TeacherAssignments();
    }
    return const _StudentAssignments();
  }
}

// ---------------------------------------------------------------------------
// Student
// ---------------------------------------------------------------------------

class _StudentAssignments extends ConsumerWidget {
  const _StudentAssignments();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final assignments = ref.watch(_forMeProvider);
    return AsyncView<List<Map<String, dynamic>>>(
      value: assignments,
      onRetry: () => ref.invalidate(_forMeProvider),
      isEmpty: (rows) => rows.isEmpty,
      builder: (rows) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(_forMeProvider),
        child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: rows.length,
          itemBuilder: (context, i) => _StudentAssignmentCard(
            assignment: rows[i],
            onDone: () => ref.invalidate(_forMeProvider),
          ),
        ),
      ),
    );
  }
}

class _StudentAssignmentCard extends ConsumerWidget {
  const _StudentAssignmentCard({required this.assignment, required this.onDone});
  final Map<String, dynamic> assignment;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final state = '${assignment['submissionState'] ?? 'pending'}';
    final graded = state == 'graded';
    final submitted = state == 'submitted' || graded;
    return Card(
      child: ListTile(
        leading: Icon(graded
            ? Icons.workspace_premium_outlined
            : submitted
                ? Icons.check_circle_outline
                : Icons.assignment_outlined),
        title: Text('${assignment['title']}'),
        subtitle: Text([
          if (assignment['dueAt'] != null)
            'Due ${formatDateTime(parseInstant(assignment['dueAt'])!)}',
          if (graded && assignment['grade'] != null)
            '${s.grade}: ${assignment['grade']}'
        ].join(' · ')),
        trailing: StatusPill(
          label: state,
          color: graded
              ? Colors.green.shade700
              : submitted
                  ? Colors.blueGrey
                  : Colors.orange.shade700,
        ),
        onTap: () => _openDetail(context, ref),
      ),
    );
  }

  Future<void> _openDetail(BuildContext context, WidgetRef ref) async {
    final s = S.of(context);
    final state = '${assignment['submissionState'] ?? 'pending'}';
    final submitted = state == 'submitted' || state == 'graded';
    final controller = TextEditingController();
    Map<String, dynamic>? submission;
    if (state != 'pending') {
      try {
        submission = await ref
            .read(apiClientProvider)
            .get('/api/v1/learning/assignments/${assignment['id']}/my-submission')
            as Map<String, dynamic>;
        controller.text = '${submission['contentText'] ?? ''}';
      } on ApiProblem {
        // no submission body available
      }
    }
    if (!context.mounted) return;

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
        child: Consumer(builder: (context, ref, _) {
          final canEdit = state == 'pending' || state == 'reopened' || state == 'draft';
          return SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('${assignment['title']}',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 8),
                if ((assignment['instructions'] ?? '').toString().isNotEmpty)
                  Text('${assignment['instructions']}'),
                const SizedBox(height: 8),
                if (assignment['maxMarks'] != null)
                  Text('Max marks: ${assignment['maxMarks']}'),
                if (state == 'graded') ...[
                  const SizedBox(height: 12),
                  SectionCard(
                    title: s.grade,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${assignment['grade']} / ${assignment['maxMarks'] ?? '-'}',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                        if ((assignment['feedback'] ?? '').toString().isNotEmpty)
                          Text('${s.feedback}: ${assignment['feedback']}'),
                      ],
                    ),
                  ),
                ] else if (canEdit) ...[
                  const SizedBox(height: 12),
                  TextField(
                    controller: controller,
                    minLines: 3,
                    maxLines: 8,
                    decoration: InputDecoration(
                      labelText: S.of(context).yourAnswer,
                      border: const OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      TextButton(
                        onPressed: () => _send(
                            sheetContext, ref, controller.text, draft: true),
                        child: Text(s.save),
                      ),
                      const Spacer(),
                      FilledButton(
                        onPressed: () => _send(
                            sheetContext, ref, controller.text, draft: false),
                        child: Text(s.submit),
                      ),
                    ],
                  ),
                ] else if (submitted) ...[
                  const SizedBox(height: 12),
                  if (controller.text.isNotEmpty) Text(controller.text),
                  const SizedBox(height: 8),
                  Text(s.submittedWaitingGrading,
                      style: Theme.of(context).textTheme.bodySmall),
                ],
              ],
            ),
          );
        }),
      ),
    );
  }

  Future<void> _send(BuildContext context, WidgetRef ref, String text,
      {required bool draft}) async {
    final s = S.of(context);
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await api.post(
        '/api/v1/learning/assignments/${assignment['id']}/${draft ? 'draft' : 'submit'}',
        data: {'contentText': text},
      );
      messenger.showSnackBar(SnackBar(content: Text(s.done)));
      if (context.mounted) Navigator.pop(context);
      onDone();
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }
}

// ---------------------------------------------------------------------------
// Teacher
// ---------------------------------------------------------------------------

class _TeacherAssignments extends ConsumerWidget {
  const _TeacherAssignments();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final assignments = ref.watch(_manageProvider);
    return Scaffold(
      body: AsyncView<List<Map<String, dynamic>>>(
        value: assignments,
        onRetry: () => ref.invalidate(_manageProvider),
        isEmpty: (rows) => rows.isEmpty,
        builder: (rows) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(_manageProvider),
          child: ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: rows.length,
            itemBuilder: (context, i) => _TeacherAssignmentCard(
              assignment: rows[i],
              onChanged: () => ref.invalidate(_manageProvider),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context, ref),
        icon: const Icon(Icons.add),
        label: Text(s.create),
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final s = S.of(context);
    final List<Map<String, dynamic>> subjects =
        ref.read(_subjectsProvider).valueOrNull ??
            await ref.read(_subjectsProvider.future);
    final List<Map<String, dynamic>> classes =
        ref.read(_classesProvider).valueOrNull ??
            await ref.read(_classesProvider.future);
    if (!context.mounted) return;
    if (subjects.isEmpty || classes.isEmpty) {
      showApiError(context, 'Create subjects and classes in Academics first.');
      return;
    }
    final formKey = GlobalKey<_AssignmentFormState>();
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.create),
        content: SizedBox(
          width: 420,
          child:
              _AssignmentForm(key: formKey, subjects: subjects, classes: classes),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(s.cancel),
          ),
          FilledButton(
            onPressed: () async {
              final form = formKey.currentState;
              if (form == null) return;
              final messenger = ScaffoldMessenger.of(context);
              try {
                await ref.read(apiClientProvider).post(
                  '/api/v1/learning/assignments',
                  data: {
                    'title': form.title.text,
                    'instructions': form.instructions.text,
                    'subjectId': form.subjectId,
                    'classGroupId': form.classGroupId,
                    'dueAt': form.dueAt?.toUtc().toIso8601String(),
                    'maxMarks': num.tryParse(form.maxMarks.text.trim()),
                  },
                );
                messenger.showSnackBar(SnackBar(content: Text(s.done)));
                if (context.mounted) Navigator.pop(context, true);
              } on ApiProblem catch (e) {
                messenger.showSnackBar(SnackBar(content: Text(e.detail)));
              }
            },
            child: Text(s.create),
          ),
        ],
      ),
    );
    if (created == true) ref.invalidate(_manageProvider);
  }
}

class _AssignmentForm extends StatefulWidget {
  const _AssignmentForm({super.key, required this.subjects, required this.classes});
  final List<Map<String, dynamic>> subjects;
  final List<Map<String, dynamic>> classes;

  @override
  State<_AssignmentForm> createState() => _AssignmentFormState();
}

class _AssignmentFormState extends State<_AssignmentForm> {
  final title = TextEditingController();
  final instructions = TextEditingController();
  final maxMarks = TextEditingController(text: '20');
  String? subjectId;
  String? classGroupId;
  DateTime? dueAt;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: title,
            decoration: InputDecoration(labelText: S.of(context).titleLabel),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: instructions,
            minLines: 2,
            maxLines: 4,
            decoration: InputDecoration(labelText: S.of(context).instructionsLabel),
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: subjectId,
            decoration: InputDecoration(labelText: S.of(context).subject),
            items: [
              for (final sub in widget.subjects)
                DropdownMenuItem(
                    value: '${sub['id']}', child: Text('${sub['name']}')),
            ],
            onChanged: (v) => setState(() => subjectId = v),
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: classGroupId,
            decoration: InputDecoration(labelText: S.of(context).classLabel),
            items: [
              for (final c in widget.classes)
                DropdownMenuItem(value: '${c['id']}', child: Text('${c['name']}')),
            ],
            onChanged: (v) => setState(() => classGroupId = v),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: maxMarks,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(labelText: S.of(context).maxMarksLabel),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: Text(dueAt == null
                    ? 'No due date'
                    : 'Due ${formatDateTime(dueAt!)}'),
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
                  if (date != null) {
                    setState(() => dueAt = DateTime(
                        date.year, date.month, date.day, 23, 59));
                  }
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

class _TeacherAssignmentCard extends ConsumerWidget {
  const _TeacherAssignmentCard({required this.assignment, required this.onChanged});
  final Map<String, dynamic> assignment;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final state = '${assignment['state'] ?? 'draft'}';
    final published = state == 'published';
    return Card(
      child: ListTile(
        title: Text('${assignment['title']}'),
        subtitle: Text([
          if (assignment['dueAt'] != null)
            'Due ${formatDateTime(parseInstant(assignment['dueAt'])!)}',
          'Max ${assignment['maxMarks'] ?? '-'}',
        ].join(' · ')),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            StatusPill(
              label: state,
              color: published ? Colors.green.shade700 : Colors.orange.shade700,
            ),
            if (!published)
              IconButton(
                tooltip: s.publish,
                icon: const Icon(Icons.publish_outlined),
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  try {
                    await ref.read(apiClientProvider).post(
                        '/api/v1/learning/assignments/${assignment['id']}/publish');
                    messenger.showSnackBar(SnackBar(content: Text(s.done)));
                    onChanged();
                  } on ApiProblem catch (e) {
                    messenger.showSnackBar(SnackBar(content: Text(e.detail)));
                  }
                },
              ),
          ],
        ),
        onTap: () => _openSubmissions(context, ref),
      ),
    );
  }

  Future<void> _openSubmissions(BuildContext context, WidgetRef ref) async {
    final s = S.of(context);
    try {
      final api = ref.read(apiClientProvider);
      final results = await Future.wait([
        api.get('/api/v1/learning/assignments/${assignment['id']}/submissions'),
        api.get('/api/v1/learning/assignments/${assignment['id']}/grades'),
      ]);
      final rows = (results[0] as List).cast<Map<String, dynamic>>();
      final names = {
        for (final g in (results[1] as List).cast<Map<String, dynamic>>())
          '${g['studentId']}': '${g['studentName']}',
      };
      if (!context.mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (sheetContext) => DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.7,
          builder: (context, controller) => ListView(
            controller: controller,
            padding: const EdgeInsets.all(16),
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('${assignment['title']} — ${s.grade}',
                        style: Theme.of(context).textTheme.titleMedium),
                  ),
                  TextButton(
                    onPressed: () async {
                      final messenger = ScaffoldMessenger.of(context);
                      try {
                        await ref.read(apiClientProvider).post(
                            '/api/v1/learning/assignments/${assignment['id']}/publish-grades');
                        messenger.showSnackBar(SnackBar(content: Text(s.done)));
                      } on ApiProblem catch (e) {
                        messenger.showSnackBar(SnackBar(content: Text(e.detail)));
                      }
                    },
                    child: Text(s.publish),
                  ),
                ],
              ),
              if (rows.isEmpty)
                EmptyState(message: s.emptyList)
              else
                for (final row in rows)
                  _GradeTile(
                    assignmentId: '${assignment['id']}',
                    submission: row,
                    studentName: names['${row['studentId']}'] ?? '${row['studentId']}',
                    onGraded: () => Navigator.pop(sheetContext),
                  ),
            ],
          ),
        ),
      );
    } on ApiProblem catch (e) {
      if (context.mounted) showApiError(context, e);
    }
  }
}

class _GradeTile extends ConsumerWidget {
  const _GradeTile({
    required this.assignmentId,
    required this.submission,
    required this.studentName,
    required this.onGraded,
  });

  final String assignmentId;
  final Map<String, dynamic> submission;
  final String studentName;
  final VoidCallback onGraded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final state = '${submission['state']}';
    final grade = submission['grade'];
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(studentName),
      subtitle: Text(state),
      trailing: grade == null
          ? (state == 'submitted'
              ? FilledButton.tonal(
                  onPressed: () => _grade(context, ref),
                  child: Text(s.grade),
                )
              : StatusPill(label: state, color: Colors.blueGrey))
          : Text('$grade', style: Theme.of(context).textTheme.titleMedium),
    );
  }

  Future<void> _grade(BuildContext context, WidgetRef ref) async {
    final s = S.of(context);
    final marks = TextEditingController();
    final feedback = TextEditingController(
        text: '${submission['feedback'] ?? ''}');
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('${s.grade}: $studentName'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: marks,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(labelText: S.of(context).marks),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: feedback,
              decoration: InputDecoration(labelText: s.feedback),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(s.cancel),
          ),
          FilledButton(
            onPressed: () async {
              final messenger = ScaffoldMessenger.of(context);
              final parsed = num.tryParse(marks.text.trim());
              if (parsed == null) return;
              try {
                await ref.read(apiClientProvider).patch(
                  '/api/v1/learning/submissions/${submission['id']}/grade',
                  data: {'marks': parsed, 'feedback': feedback.text},
                );
                messenger.showSnackBar(SnackBar(content: Text(s.done)));
                if (dialogContext.mounted) Navigator.pop(dialogContext);
                onGraded();
              } on ApiProblem catch (e) {
                messenger.showSnackBar(SnackBar(content: Text(e.detail)));
              }
            },
            child: Text(s.save),
          ),
        ],
      ),
    );
  }
}
