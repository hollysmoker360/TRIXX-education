import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _classesProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/academics/classes') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

final _subjectsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/academics/subjects') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

final _streamsProvider = FutureProvider.family<List<Map<String, dynamic>>, String>((ref, classGroupId) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/academics/streams', query: {'classGroupId': classGroupId})
          as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

class LessonsScreen extends ConsumerStatefulWidget {
  const LessonsScreen({super.key});

  @override
  ConsumerState<LessonsScreen> createState() => _LessonsScreenState();
}

class _LessonsScreenState extends ConsumerState<LessonsScreen> {
  String? _classGroupId;
  String? _streamId;
  String? _subjectId;
  final _topicController = TextEditingController(text: 'Lesson');

  @override
  void dispose() {
    _topicController.dispose();
    super.dispose();
  }

  Future<void> _joinLesson() async {
    final s = S.of(context);
    if (_classGroupId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(S.of(context).selectClassFirst)));
      return;
    }
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final room = await api.get('/api/v1/lessons/rooms', query: {
        'classGroupId': _classGroupId,
        if (_streamId != null) 'streamId': _streamId,
        if (_subjectId != null) 'subjectId': _subjectId,
        'topic': _topicController.text.trim().isEmpty ? 'Lesson' : _topicController.text.trim(),
      }) as Map<String, dynamic>;
      final url = Uri.tryParse('${room['url']}');
      if (url == null) {
        messenger.showSnackBar(SnackBar(content: Text(s.noRoomUrl)));
        return;
      }
      await launchUrl(url, webOnlyWindowName: '_blank');
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final classes = ref.watch(_classesProvider);
    final subjects = ref.watch(_subjectsProvider);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        SectionCard(
          title: s.lessons,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                  '${session.membership?.schoolName ?? ''} · ${session.membership?.roleLabel ?? ''}',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 16),
              AsyncView<List<Map<String, dynamic>>>(
                value: classes,
                onRetry: () => ref.invalidate(_classesProvider),
                isEmpty: (data) => data.isEmpty,
                builder: (allClasses) => DropdownFormField<String>(
                  value: _classGroupId,
                  items: allClasses
                      .map((c) => DropdownMenuItem<String>(
                          value: '${c['id']}', child: Text('${c['name']}')))
                      .toList(),
                  onChanged: (v) {
                    setState(() {
                      _classGroupId = v;
                      _streamId = null;
                    });
                  },
                  label: s.classGroup,
                ),
              ),
              const SizedBox(height: 12),
              if (_classGroupId != null)
                AsyncView<List<Map<String, dynamic>>>(
                  value: ref.watch(_streamsProvider(_classGroupId!)),
                  onRetry: () => ref.invalidate(_streamsProvider(_classGroupId!)),
                  isEmpty: (data) => data.isEmpty,
                  builder: (streams) => DropdownFormField<String>(
                    value: _streamId,
                    items: streams
                        .map((s) => DropdownMenuItem<String>(
                            value: '${s['id']}', child: Text('${s['name']}')))
                        .toList(),
                    onChanged: (v) => setState(() => _streamId = v),
                    label: s.stream,
                  ),
                ),
              const SizedBox(height: 12),
              AsyncView<List<Map<String, dynamic>>>(
                value: subjects,
                onRetry: () => ref.invalidate(_subjectsProvider),
                isEmpty: (data) => data.isEmpty,
                builder: (allSubjects) => DropdownFormField<String>(
                  value: _subjectId,
                  items: allSubjects
                      .map((c) => DropdownMenuItem<String>(
                          value: '${c['id']}', child: Text('${c['name']}')))
                      .toList(),
                  onChanged: (v) => setState(() => _subjectId = v),
                  label: s.subject,
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _topicController,
                decoration: InputDecoration(
                  labelText: s.topic,
                  border: const OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              FilledButton.tonalIcon(
                onPressed: _joinLesson,
                icon: const Icon(Icons.videocam_outlined, size: 18),
                label: Text(s.openRoom),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// A dropdown wrapped in a text-field-style decoration so it matches the surrounding form.
class DropdownFormField<T> extends StatelessWidget {
  const DropdownFormField({
    super.key,
    required this.value,
    required this.items,
    required this.onChanged,
    required this.label,
  });

  final T? value;
  final List<DropdownMenuItem<T>> items;
  final void Function(T?) onChanged;
  final String label;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<T>(
      initialValue: value,
      items: items,
      onChanged: onChanged,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 16),
      ),
      icon: const Icon(Icons.arrow_drop_down),
      isExpanded: true,
      validator: (v) => v == null ? 'Required' : null,
    );
  }
}
