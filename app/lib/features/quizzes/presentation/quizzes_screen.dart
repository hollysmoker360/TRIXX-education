import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _forMeProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/quizzes/for-me');
  return (data as List).cast<Map<String, dynamic>>();
});

final _manageProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/quizzes');
  return (data as List).cast<Map<String, dynamic>>();
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

class QuizzesScreen extends ConsumerWidget {
  const QuizzesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    if (session.can('assignment.create')) {
      return const _TeacherQuizzes();
    }
    return const _StudentQuizzes();
  }
}

// ---------------------------------------------------------------------------
// Student
// ---------------------------------------------------------------------------

class _StudentQuizzes extends ConsumerWidget {
  const _StudentQuizzes();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final quizzes = ref.watch(_forMeProvider);
    return AsyncView<List<Map<String, dynamic>>>(
      value: quizzes,
      onRetry: () => ref.invalidate(_forMeProvider),
      isEmpty: (rows) => rows.isEmpty,
      builder: (rows) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(_forMeProvider),
        child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: rows.length,
          itemBuilder: (context, i) => _StudentQuizCard(
            quiz: rows[i],
            onDone: () => ref.invalidate(_forMeProvider),
          ),
        ),
      ),
    );
  }
}

class _StudentQuizCard extends ConsumerWidget {
  const _StudentQuizCard({required this.quiz, required this.onDone});
  final Map<String, dynamic> quiz;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = '${quiz['attemptState'] ?? 'not_started'}';
    final used = (quiz['attemptsUsed'] as num?)?.toInt() ?? 0;
    final max = (quiz['maxAttempts'] as num?)?.toInt() ?? 1;
    final done = state == 'submitted' || state == 'graded';
    return Card(
      child: ListTile(
        leading: const Icon(Icons.quiz_outlined),
        title: Text('${quiz['title']}'),
        subtitle: Text([
          '${quiz['questionCount']} questions',
          if (quiz['timeLimitMinutes'] != null)
            '${quiz['timeLimitMinutes']} min',
          'Attempts $used/$max',
        ].join(' · ')),
        trailing: done
            ? StatusPill(
                label: state,
                color: state == 'graded'
                    ? Colors.green.shade700
                    : Colors.blueGrey,
              )
            : FilledButton.tonal(
                onPressed: used >= max
                    ? null
                    : () => _start(context, ref),
                child: Text(S.of(context).start),
              ),
      ),
    );
  }

  Future<void> _start(BuildContext context, WidgetRef ref) async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final start = await api.post(
          '/api/v1/learning/quizzes/${quiz['id']}/attempts') as Map<String, dynamic>;
      if (!context.mounted) return;
      await context.push(AppRoutes.quizAttemptLocation('${quiz['id']}'), extra: start);
      onDone();
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }
}

class QuizAttemptScreen extends ConsumerStatefulWidget {
  const QuizAttemptScreen({super.key, required this.quizId, required this.start});
  final String quizId;
  final Map<String, dynamic> start;

  @override
  ConsumerState<QuizAttemptScreen> createState() => _QuizAttemptScreenState();
}

class _QuizAttemptScreenState extends ConsumerState<QuizAttemptScreen> {
  final Map<String, Object> _answers = {};
  final Map<String, TextEditingController> _texts = {};
  bool _busy = false;

  @override
  void dispose() {
    for (final controller in _texts.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final attempt = widget.start['attempt'] as Map<String, dynamic>;
    final questions = (widget.start['questions'] as List? ?? const [])
        .cast<Map<String, dynamic>>();
    return Scaffold(
      appBar: AppBar(title: Text('${attempt['attemptNo']}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          for (final question in questions) _question(question),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _busy ? null : _submit,
        icon: const Icon(Icons.send_outlined),
        label: Text(s.submit),
      ),
    );
  }

  Widget _question(Map<String, dynamic> question) {
    final id = '${question['id']}';
    final type = '${question['type']}';
    final prompt = '${question['position']}. ${question['prompt']}';
    final options = (question['options'] as List? ?? const []);
    switch (type) {
      case 'mcq':
        return Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.all(12),
                child: Text(prompt, style: Theme.of(context).textTheme.titleSmall),
              ),
              for (var i = 0; i < options.length; i++)
                ListTile(
                  dense: true,
                  leading: Icon(_answers[id] == i
                      ? Icons.radio_button_checked
                      : Icons.radio_button_unchecked),
                  title: Text('${options[i]}'),
                  onTap: () => setState(() => _answers[id] = i),
                ),
            ],
          ),
        );
      case 'true_false':
        return Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.all(12),
                child: Text(prompt, style: Theme.of(context).textTheme.titleSmall),
              ),
              for (final value in const [true, false])
                ListTile(
                  dense: true,
                  leading: Icon(_answers[id] == value
                      ? Icons.radio_button_checked
                      : Icons.radio_button_unchecked),
                  title: Text(value ? 'True' : 'False'),
                  onTap: () => setState(() => _answers[id] = value),
                ),
            ],
          ),
        );
      default:
        final controller = _texts.putIfAbsent(id, () {
          final c = TextEditingController();
          c.addListener(() => _answers[id] = c.text);
          return c;
        });
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(prompt, style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                TextField(
                  controller: controller,
                  minLines: 1,
                  maxLines: 4,
                  decoration: InputDecoration(
                    hintText: S.of(context).yourAnswer,
                    border: const OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
        );
    }
  }

  Future<void> _submit() async {
    final s = S.of(context);
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final attempt = widget.start['attempt'] as Map<String, dynamic>;
    setState(() => _busy = true);
    try {
      await api.post(
        '/api/v1/learning/quizzes/${widget.quizId}/attempts/${attempt['id']}/submit',
        data: {'answers': _answers},
      );
      messenger.showSnackBar(SnackBar(content: Text(s.done)));
      if (mounted) context.pop();
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

// ---------------------------------------------------------------------------
// Teacher
// ---------------------------------------------------------------------------

class _TeacherQuizzes extends ConsumerWidget {
  const _TeacherQuizzes();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final quizzes = ref.watch(_manageProvider);
    return Scaffold(
      body: AsyncView<List<Map<String, dynamic>>>(
        value: quizzes,
        onRetry: () => ref.invalidate(_manageProvider),
        isEmpty: (rows) => rows.isEmpty,
        builder: (rows) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(_manageProvider),
          child: ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: rows.length,
            itemBuilder: (context, i) {
              final quiz = rows[i];
              final published = '${quiz['state']}' == 'published';
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.quiz_outlined),
                  title: Text('${quiz['title']}'),
                  subtitle: Text([
                    '${(quiz['questions'] as List? ?? const []).length} questions',
                    if (quiz['timeLimitMinutes'] != null)
                      '${quiz['timeLimitMinutes']} min',
                    'Max ${quiz['maxAttempts']} attempts',
                  ].join(' · ')),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      StatusPill(
                        label: '${quiz['state']}',
                        color: published
                            ? Colors.green.shade700
                            : Colors.orange.shade700,
                      ),
                      if (!published)
                        IconButton(
                          tooltip: s.publish,
                          icon: const Icon(Icons.publish_outlined),
                          onPressed: () async {
                            final messenger = ScaffoldMessenger.of(context);
                            try {
                              await ref.read(apiClientProvider).post(
                                  '/api/v1/learning/quizzes/${quiz['id']}/publish');
                              messenger.showSnackBar(
                                  SnackBar(content: Text(s.done)));
                              ref.invalidate(_manageProvider);
                            } on ApiProblem catch (e) {
                              messenger.showSnackBar(
                                  SnackBar(content: Text(e.detail)));
                            }
                          },
                        ),
                    ],
                  ),
                ),
              );
            },
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
    final formKey = GlobalKey<_QuizFormState>();
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.create),
        content: SizedBox(
          width: 460,
          child: _QuizForm(key: formKey, subjects: subjects, classes: classes),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(s.cancel),
          ),
          FilledButton(
            onPressed: () async {
              final form = formKey.currentState;
              if (form == null || form.questions.isEmpty) return;
              final messenger = ScaffoldMessenger.of(context);
              try {
                await ref.read(apiClientProvider).post(
                  '/api/v1/learning/quizzes',
                  data: {
                    'title': form.title.text,
                    'subjectId': form.subjectId,
                    'classGroupId': form.classGroupId,
                    'timeLimitMinutes': int.tryParse(form.timeLimit.text),
                    'maxAttempts': int.tryParse(form.maxAttempts.text) ?? 1,
                    'shuffle': form.shuffle,
                    'questions': form.questions,
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

class _QuizForm extends StatefulWidget {
  const _QuizForm({super.key, required this.subjects, required this.classes});
  final List<Map<String, dynamic>> subjects;
  final List<Map<String, dynamic>> classes;

  @override
  State<_QuizForm> createState() => _QuizFormState();
}

class _QuizFormState extends State<_QuizForm> {
  final title = TextEditingController();
  final timeLimit = TextEditingController();
  final maxAttempts = TextEditingController(text: '1');
  String? subjectId;
  String? classGroupId;
  bool shuffle = false;
  final List<Map<String, dynamic>> questions = [];

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
          DropdownButtonFormField<String>(
            initialValue: subjectId,
            decoration: InputDecoration(labelText: S.of(context).subject),
            items: [
              for (final s in widget.subjects)
                DropdownMenuItem(value: '${s['id']}', child: Text('${s['name']}')),
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
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: timeLimit,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(labelText: S.of(context).timeLimitMin),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: maxAttempts,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(labelText: S.of(context).maxAttempts),
                ),
              ),
            ],
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(S.of(context).shuffleQuestions),
            value: shuffle,
            onChanged: (v) => setState(() => shuffle = v),
          ),
          const Divider(),
          for (var i = 0; i < questions.length; i++)
            ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              leading: Text('${i + 1}'),
              title: Text('${questions[i]['prompt']}'),
              subtitle: Text('${questions[i]['type']}'),
              trailing: IconButton(
                icon: const Icon(Icons.delete_outline),
                onPressed: () => setState(() => questions.removeAt(i)),
              ),
            ),
          OutlinedButton.icon(
            onPressed: _addQuestion,
            icon: const Icon(Icons.add),
            label: Text(S.of(context).addQuestion),
          ),
        ],
      ),
    );
  }

  Future<void> _addQuestion() async {
    final question = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => const _QuestionDialog(),
    );
    if (question != null) setState(() => questions.add(question));
  }
}

class _QuestionDialog extends StatefulWidget {
  const _QuestionDialog();

  @override
  State<_QuestionDialog> createState() => _QuestionDialogState();
}

class _QuestionDialogState extends State<_QuestionDialog> {
  final prompt = TextEditingController();
  final options = TextEditingController();
  final answerText = TextEditingController();
  final points = TextEditingController(text: '1');
  String type = 'mcq';
  int correctIndex = 0;
  bool correctBool = true;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(S.of(context).question),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            DropdownButtonFormField<String>(
              initialValue: type,
              decoration: InputDecoration(labelText: S.of(context).typeLabel),
              items: [
                DropdownMenuItem(value: 'mcq', child: Text(S.of(context).multipleChoice)),
                DropdownMenuItem(value: 'true_false', child: Text(S.of(context).trueFalse)),
                DropdownMenuItem(value: 'short_answer', child: Text(S.of(context).shortAnswer)),
              ],
              onChanged: (v) => setState(() => type = v!),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: prompt,
              minLines: 1,
              maxLines: 3,
              decoration: InputDecoration(labelText: S.of(context).promptLabel),
            ),
            if (type == 'mcq') ...[
              const SizedBox(height: 10),
              TextField(
                controller: options,
                decoration: InputDecoration(
                  labelText: S.of(context).optionsCommaSeparated,
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: answerText,
                keyboardType: TextInputType.number,
                decoration:
                    InputDecoration(labelText: S.of(context).correctOptionNumber),
              ),
            ],
            if (type == 'true_false') ...[
              const SizedBox(height: 10),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(S.of(context).answerIsTrue),
                value: correctBool,
                onChanged: (v) => setState(() => correctBool = v),
              ),
            ],
            if (type == 'short_answer') ...[
              const SizedBox(height: 10),
              TextField(
                controller: answerText,
                decoration: InputDecoration(labelText: S.of(context).expectedAnswer),
              ),
            ],
            const SizedBox(height: 10),
            TextField(
              controller: points,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(labelText: S.of(context).pointsLabel),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(S.of(context).cancel),
        ),
        FilledButton(
          onPressed: () {
            final list = options.text
                .split(',')
                .map((o) => o.trim())
                .where((o) => o.isNotEmpty)
                .toList();
            final Map<String, dynamic> answerKey;
            switch (type) {
              case 'mcq':
                final index = (int.tryParse(answerText.text.trim()) ?? 1) - 1;
                if (list.length < 2 || index < 0 || index >= list.length) return;
                answerKey = {'index': index};
              case 'true_false':
                answerKey = {'value': correctBool};
              default:
                if (answerText.text.trim().isEmpty) return;
                answerKey = {'text': answerText.text.trim()};
            }
            Navigator.pop(context, {
              'type': type,
              'prompt': prompt.text,
              'options': type == 'mcq' ? list : type == 'true_false' ? [true, false] : null,
              'answerKey': answerKey,
              'points': num.tryParse(points.text.trim()) ?? 1,
            });
          },
          child: Text(S.of(context).save),
        ),
      ],
    );
  }
}
