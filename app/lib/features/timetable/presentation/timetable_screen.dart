import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/format.dart';
import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _publishedProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return await api.get('/api/v1/timetable/published') as Map<String, dynamic>;
});

final _versionsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/timetable/versions') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

final _termsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/academics/terms') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

final _yearsProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return (await api.get('/api/v1/academics/years') as List<dynamic>)
      .cast<Map<String, dynamic>>();
});

/// Learner enrollment (null for staff). Lets the screen scope the published
/// timetable to the signed-in learner's class.
final _enrollmentProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  try {
    return await api.get('/api/v1/people/me/student-detail') as Map<String, dynamic>;
  } on ApiProblem {
    return null;
  }
});

class TimetableScreen extends ConsumerStatefulWidget {
  const TimetableScreen({super.key});

  @override
  ConsumerState<TimetableScreen> createState() => _TimetableScreenState();
}

class _TimetableScreenState extends ConsumerState<TimetableScreen> {
  int _day = DateTime.now().weekday.clamp(1, 5);

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final published = ref.watch(_publishedProvider);
    final enrollment = ref.watch(_enrollmentProvider);
    final versions = ref.watch(_versionsProvider);

    return AsyncView<Map<String, dynamic>>(
      value: published,
      onRetry: () {
        ref.invalidate(_publishedProvider);
        ref.invalidate(_enrollmentProvider);
      },
      isEmpty: (data) =>
          (data['entries'] as List? ?? const []).isEmpty,
      builder: (data) {
        final all = (data['entries'] as List? ?? const [])
            .cast<Map<String, dynamic>>();
        final entries = _scope(all, session, enrollment.valueOrNull);
        final byDay = entries.where((e) => e['dayOfWeek'] == _day).toList()
          ..sort((a, b) => (a['periodSeq'] as num).compareTo(b['periodSeq'] as num));
        final summary = data['summary'] as Map<String, dynamic>?;

        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      '${s.timetable} · ${summary?['name'] ?? ''}',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  if (summary?['publishedAt'] != null)
                    StatusPill(
                      label: S.of(context).published,
                      color: Colors.green.shade700,
                    ),
                ],
              ),
            ),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: [
                  for (var d = 1; d <= 5; d++) ...[
                    ChoiceChip(
                      label: Text(dayName(d)),
                      selected: _day == d,
                      onSelected: (_) => setState(() => _day = d),
                    ),
                    const SizedBox(width: 8),
                  ],
                ],
              ),
            ),
            Expanded(
              child: byDay.isEmpty
                  ? EmptyState(message: s.emptyList)
                  : RefreshIndicator(
                      onRefresh: () async {
                        ref.invalidate(_publishedProvider);
                        ref.invalidate(_enrollmentProvider);
                      },
                      child: ListView.separated(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                        itemCount: byDay.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 8),
                        itemBuilder: (context, i) => _EntryCard(
                          entry: byDay[i],
                          showTeacher: true,
                          onJoin: () => _joinLesson(byDay[i]),
                        ),
                      ),
                    ),
            ),
            if (_canManage())
              _ManagementSection(
                versions: versions.valueOrNull ?? const <Map<String, dynamic>>[],
                onCreateVersion: () => _createVersion(),
                onGenerate: _generate,
                onValidate: _validate,
                onPublish: _publish,
                onArchive: _archive,
              ),
          ],
        );
      },
    );
  }

  List<Map<String, dynamic>> _scope(
    List<Map<String, dynamic>> all,
    SessionState session,
    Map<String, dynamic>? enrollment,
  ) {
    final classGroupId =
        (enrollment?['enrollment'] as Map<String, dynamic>?)?['classGroupId'];
    if (classGroupId != null) {
      return all.where((e) => '${e['classGroupId']}' == '$classGroupId').toList();
    }
    if (session.can('timetable.manage')) return all;
    final membershipId = session.membership?.membershipId;
    return all.where((e) => '${e['teacherMembershipId']}' == '$membershipId').toList();
  }

  Future<void> _joinLesson(Map<String, dynamic> entry) async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final room = await api.get('/api/v1/lessons/rooms', query: {
        'classGroupId': entry['classGroupId'],
        if (entry['subjectId'] != null) 'subjectId': entry['subjectId'],
        'topic': '${entry['subjectName'] ?? 'Lesson'}',
      }) as Map<String, dynamic>;
      final url = Uri.tryParse('${room['url']}');
      if (url == null) return;
      await launchUrl(url, webOnlyWindowName: '_blank');
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  bool _canManage() => ref.read(sessionProvider).can('timetable.manage');

  List<Map<String, dynamic>> get _versions =>
      ref.watch(_versionsProvider).valueOrNull ?? const <Map<String, dynamic>>[];

  List<Map<String, dynamic>> get _terms =>
      ref.watch(_termsProvider).valueOrNull ?? const <Map<String, dynamic>>[];

  List<Map<String, dynamic>> get _years =>
      ref.watch(_yearsProvider).valueOrNull ?? const <Map<String, dynamic>>[];

  Future<void> _createVersion() async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final terms = _terms;
    final years = _years;
    String? termId;
    if (terms.isNotEmpty) {
      termId = '${terms.first['id']}';
    }
    final created = await api.post('/api/v1/timetable/versions', data: {
      'academicYearId': years.isEmpty ? null : '${years.first['id']}',
      'termId': termId,
      'name': 'Timetable for ${DateTime.now().toLocal().toIso8601String().substring(0, 10)}',
      'note': '',
    }) as Map<String, dynamic>;
    if (!mounted) return;
    messenger.showSnackBar(SnackBar(content: Text('${created['name']} created.')));
  }

  Future<void> _generate() async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final s = S.of(context);
    final versions = _versions;
    if (versions.isEmpty) return;
    final version = versions.first;
    await api.post('/api/v1/timetable/versions/${version['id']}/generate', data: {
      'seed': null,
      'attempts': 1,
      'days': <int>[1, 2, 3, 4, 5],
    });
    if (!mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(s.timetableGenerated)));
    ref.invalidate(_publishedProvider);
  }

  Future<void> _validate() async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final s = S.of(context);
    final versions = _versions;
    if (versions.isEmpty) return;
    final version = versions.first;
    await api.post('/api/v1/timetable/versions/${version['id']}/validate');
    if (!mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(s.validationComplete)));
  }

  Future<void> _publish() async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final s = S.of(context);
    final versions = _versions;
    if (versions.isEmpty) return;
    final version = versions.first;
    await api.post('/api/v1/timetable/versions/${version['id']}/publish');
    if (!mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(s.timetablePublished)));
    ref.invalidate(_publishedProvider);
  }

  Future<void> _archive() async {
    final api = ref.read(apiClientProvider);
    final messenger = ScaffoldMessenger.of(context);
    final s = S.of(context);
    final versions = _versions;
    if (versions.isEmpty) return;
    final version = versions.first;
    await api.post('/api/v1/timetable/versions/${version['id']}/archive');
    if (!mounted) return;
    messenger.showSnackBar(SnackBar(content: Text(s.timetableArchived)));
  }
}

/// Staff-only section managing timetable versions: create a version, generate entries, validate,
/// publish and archive (spec 12.1).
class _ManagementSection extends ConsumerWidget {
  const _ManagementSection({
    required this.versions,
    required this.onCreateVersion,
    required this.onGenerate,
    required this.onValidate,
    required this.onPublish,
    required this.onArchive,
  });

  final List<Map<String, dynamic>> versions;
  final VoidCallback onCreateVersion;
  final VoidCallback onGenerate;
  final VoidCallback onValidate;
  final VoidCallback onPublish;
  final VoidCallback onArchive;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    return SectionCard(
      title: s.timetableManage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: onCreateVersion,
              icon: const Icon(Icons.add),
              label: Text(s.createVersion),
            ),
          ),
          const SizedBox(height: 12),
          if (versions.isEmpty)
            Text(s.noVersionsYet)
          else
            SizedBox(
              height: 320,
              child: ListView.builder(
                shrinkWrap: true,
                padding: EdgeInsets.zero,
                itemCount: versions.length,
                itemBuilder: (context, i) {
                  final version = versions[i];
                  final state = '${version['state'] ?? ''}';
                  return ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text('${version['name'] ?? ''} '
                        '(${version['entryCount'] ?? 0} entries)'),
                    subtitle: Text('State: $state'),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (state == 'draft' || state == 'active')
                          IconButton(
                            tooltip: S.of(context).generate,
                            icon: const Icon(Icons.psychology, size: 20),
                            onPressed: onGenerate,
                          ),
                        if (state == 'draft' || state == 'active')
                          IconButton(
                            tooltip: S.of(context).validate,
                            icon: const Icon(Icons.fact_check_outlined, size: 20),
                            onPressed: onValidate,
                          ),
                        if (state == 'active')
                          IconButton(
                            tooltip: S.of(context).publish,
                            icon: const Icon(Icons.publish, size: 20),
                            onPressed: onPublish,
                          ),
                        if (state == 'active')
                          IconButton(
                            tooltip: S.of(context).archiveAction,
                            icon: const Icon(Icons.archive_outlined, size: 20),
                            onPressed: onArchive,
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _EntryCard extends StatelessWidget {
  const _EntryCard({
    required this.entry,
    required this.showTeacher,
    required this.onJoin,
  });

  final Map<String, dynamic> entry;
  final bool showTeacher;
  final VoidCallback onJoin;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final span = (entry['span'] as num?)?.toInt() ?? 1;
    return Card(
      margin: EdgeInsets.zero,
      child: ListTile(
        leading: CircleAvatar(
          child: Text('P${entry['periodSeq']}', style: const TextStyle(fontSize: 12)),
        ),
        title: Text('${entry['subjectName'] ?? 'Lesson'}'),
        subtitle: Text([
          if (span > 1) '$span periods',
          '${entry['className'] ?? ''}',
          if (showTeacher) '${entry['teacherName'] ?? ''}',
        ].where((t) => t.trim().isNotEmpty).join(' · ')),
        trailing: IconButton(
          tooltip: S.of(context).joinLesson,
          icon: Icon(Icons.videocam_outlined, color: theme.colorScheme.primary),
          onPressed: onJoin,
        ),
      ),
    );
  }
}
