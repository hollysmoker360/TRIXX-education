import 'dart:typed_data';

import 'package:file_saver/file_saver.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

final _overviewAnalyticsProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/analytics/overview');
  return data as Map<String, dynamic>;
});

final _academicPerformanceProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/analytics/academic-performance');
  return data as Map<String, dynamic>;
});

final _cbeMasteryProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/analytics/competency-mastery');
  return data as Map<String, dynamic>;
});

final _attendanceAnalyticsProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/analytics/attendance');
  return data as Map<String, dynamic>;
});

final _classesProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/academics/classes');
  return (data as List).cast<Map<String, dynamic>>();
});

class ReportsScreen extends ConsumerStatefulWidget {
  const ReportsScreen({super.key});

  static String formatDate(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  @override
  ConsumerState<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends ConsumerState<ReportsScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  // Attendance Export state
  String? _exportClassGroupId;
  DateTime _from = DateTime.now().subtract(const Duration(days: 30));
  DateTime _to = DateTime.now();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _exportAttendance(String format) async {
    final s = S.of(context);
    final classGroupId = _exportClassGroupId;
    if (classGroupId == null) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final bytes = await ref.read(apiClientProvider).getBytes(
        '/api/v1/attendance/reports/class.$format',
        query: {
          'classGroupId': classGroupId,
          'from': ReportsScreen.formatDate(_from),
          'to': ReportsScreen.formatDate(_to),
        },
      );
      await FileSaver.instance.saveFile(
        name: 'attendance-${ReportsScreen.formatDate(_from)}-${ReportsScreen.formatDate(_to)}',
        bytes: Uint8List.fromList(bytes),
        ext: format,
        mimeType: format == 'pdf' ? MimeType.pdf : MimeType.csv,
      );
      messenger.showSnackBar(SnackBar(content: Text(s.download)));
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          color: Theme.of(context).colorScheme.surface,
          child: TabBar(
            controller: _tabController,
            isScrollable: true,
            tabs: const [
              Tab(icon: Icon(Icons.dashboard_outlined), text: 'Overview'),
              Tab(icon: Icon(Icons.school_outlined), text: 'Academic Performance'),
              Tab(icon: Icon(Icons.workspace_premium_outlined), text: 'CBE Mastery'),
              Tab(icon: Icon(Icons.fact_check_outlined), text: 'Attendance'),
              Tab(icon: Icon(Icons.file_download_outlined), text: 'Exports'),
            ],
          ),
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _OverviewTab(),
              _AcademicPerformanceTab(),
              _CbeMasteryTab(),
              _AttendanceTab(),
              _ExportsTab(
                classGroupId: _exportClassGroupId,
                from: _from,
                to: _to,
                onClassChanged: (v) => setState(() => _exportClassGroupId = v),
                onFromChanged: (d) => setState(() => _from = d),
                onToChanged: (d) => setState(() => _to = d),
                onExport: _exportAttendance,
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _OverviewTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overviewAsync = ref.watch(_overviewAnalyticsProvider);

    return overviewAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => ErrorView(error: e, onRetry: () => ref.refresh(_overviewAnalyticsProvider)),
      data: (data) {
        final enrolled = data['enrolledLearners'] ?? 0;
        final attendanceRate = (data['attendanceRate'] as num?)?.toDouble() ?? 0.0;
        final assessmentAvg = (data['assessmentAverage'] as num?)?.toDouble() ?? 0.0;
        final cbeRate = (data['competencyOnTrackRate'] as num?)?.toDouble() ?? 0.0;
        final priorityItems = (data['priorityFollowUps'] as List? ?? const []).cast<Map<String, dynamic>>();
        final performanceTrend = (data['performanceTrend'] as List? ?? const []).cast<Map<String, dynamic>>();

        return ListView(
          padding: const EdgeInsets.all(AppSpacing.md),
          children: [
            Wrap(
              spacing: AppSpacing.md,
              runSpacing: AppSpacing.md,
              children: [
                _KpiMetricCard(
                  title: 'Enrolled Learners',
                  value: '$enrolled',
                  subtitle: 'Active students',
                  icon: Icons.people_outline,
                  accentColor: AppColors.navy,
                ),
                _KpiMetricCard(
                  title: 'Attendance Rate',
                  value: '${attendanceRate.toStringAsFixed(1)}%',
                  subtitle: 'Overall attendance',
                  icon: Icons.check_circle_outline,
                  accentColor: AppColors.tealBrand,
                ),
                _KpiMetricCard(
                  title: 'Assessment Average',
                  value: '${assessmentAvg.toStringAsFixed(1)}%',
                  subtitle: 'Term to date',
                  icon: Icons.grade_outlined,
                  accentColor: Colors.indigo,
                ),
                _KpiMetricCard(
                  title: 'CBE Mastery Rate',
                  value: '${cbeRate.toStringAsFixed(1)}%',
                  subtitle: 'Competencies on track',
                  icon: Icons.stars_outlined,
                  accentColor: AppColors.amber,
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Priority Follow-Ups',
              child: Column(
                children: [
                  for (final item in priorityItems)
                    ListTile(
                      leading: CircleAvatar(
                        backgroundColor: _severityColor(item['severity'] as String?).withOpacity(0.15),
                        child: Icon(
                          Icons.warning_amber_rounded,
                          color: _severityColor(item['severity'] as String?),
                        ),
                      ),
                      title: Text(
                        '${item['title']}',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      subtitle: Text('${item['description']}'),
                      trailing: StatusPill(
                        label: '${item['severity'] ?? 'INFO'}',
                        color: _severityColor(item['severity'] as String?),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Academic Performance Trend by Term',
              child: SizedBox(
                height: 180,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    for (final termData in performanceTrend)
                      Column(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Text(
                            '${(termData['averageScore'] as num?)?.toStringAsFixed(1)}%',
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                          const SizedBox(height: AppSpacing.xs),
                          Container(
                            width: 48,
                            height: ((termData['averageScore'] as num?)?.toDouble() ?? 50.0) * 1.2,
                            decoration: const BoxDecoration(
                              color: AppColors.tealBrand,
                              borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.sm)),
                            ),
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          Text('${termData['term']}'),
                        ],
                      ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  Color _severityColor(String? severity) {
    switch (severity) {
      case 'AMBER':
        return AppColors.amber;
      case 'RED':
        return AppColors.errorLight;
      case 'BLUE':
      default:
        return AppColors.navy;
    }
  }
}

class _AcademicPerformanceTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final performanceAsync = ref.watch(_academicPerformanceProvider);

    return performanceAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => ErrorView(error: e, onRetry: () => ref.refresh(_academicPerformanceProvider)),
      data: (data) {
        final distributions = (data['scoreDistributions'] as List? ?? const []).cast<Map<String, dynamic>>();
        final subjects = (data['subjectPerformance'] as List? ?? const []).cast<Map<String, dynamic>>();
        final classComparisons = (data['classComparisons'] as List? ?? const []).cast<Map<String, dynamic>>();

        return ListView(
          padding: const EdgeInsets.all(AppSpacing.md),
          children: [
            SectionCard(
              title: 'Score Distribution Bands',
              child: Column(
                children: [
                  for (final band in distributions)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                      child: Row(
                        children: [
                          SizedBox(
                            width: 180,
                            child: Text(
                              '${band['band']}',
                              style: const TextStyle(fontWeight: FontWeight.w500),
                            ),
                          ),
                          Expanded(
                            child: LinearProgressIndicator(
                              value: ((band['percentage'] as num?)?.toDouble() ?? 0.0) / 100.0,
                              minHeight: 12,
                              borderRadius: BorderRadius.circular(AppRadius.sm),
                              backgroundColor: AppColors.borderLight,
                              color: AppColors.tealBrand,
                            ),
                          ),
                          const SizedBox(width: AppSpacing.sm),
                          Text('${band['count']} learners (${band['percentage']}%)'),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Subject Performance Overview',
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: DataTable(
                  columns: const [
                    DataColumn(label: Text('Code')),
                    DataColumn(label: Text('Subject')),
                    DataColumn(label: Text('Average Score'), numeric: true),
                    DataColumn(label: Text('Pass Rate'), numeric: true),
                  ],
                  rows: [
                    for (final sub in subjects)
                      DataRow(cells: [
                        DataCell(Text('${sub['code']}')),
                        DataCell(Text('${sub['subjectName']}')),
                        DataCell(Text('${sub['averageScore']}%')),
                        DataCell(Text('${sub['passRate']}%')),
                      ]),
                  ],
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Class Comparisons',
              child: Wrap(
                spacing: AppSpacing.md,
                runSpacing: AppSpacing.md,
                children: [
                  for (final c in classComparisons)
                    Container(
                      width: 200,
                      padding: const EdgeInsets.all(AppSpacing.md),
                      decoration: BoxDecoration(
                        border: Border.all(color: AppColors.borderLight),
                        borderRadius: BorderRadius.circular(AppRadius.md),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${c['className']}',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                          ),
                          const SizedBox(height: AppSpacing.xs),
                          Text('Average: ${c['averageScore']}%'),
                          Text('Assessments: ${c['assessmentCount']}'),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _CbeMasteryTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cbeAsync = ref.watch(_cbeMasteryProvider);

    return cbeAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => ErrorView(error: e, onRetry: () => ref.refresh(_cbeMasteryProvider)),
      data: (data) {
        final breakdown = (data['masteryBreakdown'] as List? ?? const []).cast<Map<String, dynamic>>();
        final competencies = (data['competencies'] as List? ?? const []).cast<Map<String, dynamic>>();
        final coverageRate = (data['evidenceCoverageRate'] as num?)?.toDouble() ?? 0.0;

        return ListView(
          padding: const EdgeInsets.all(AppSpacing.md),
          children: [
            SectionCard(
              title: 'Evidence Coverage Summary',
              child: Row(
                children: [
                  const Icon(Icons.fact_check_outlined, color: AppColors.tealBrand, size: 36),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Overall Evidence Coverage: ${coverageRate.toStringAsFixed(1)}%',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                        ),
                        const SizedBox(height: AppSpacing.xs),
                        LinearProgressIndicator(
                          value: coverageRate / 100.0,
                          minHeight: 10,
                          borderRadius: BorderRadius.circular(AppRadius.sm),
                          color: AppColors.tealBrand,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Competency Proficiency Levels',
              child: Column(
                children: [
                  for (final level in breakdown)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('${level['level']}', style: const TextStyle(fontWeight: FontWeight.w500)),
                          Text('${level['count']} competencies (${level['percentage']}%)'),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Competency Heatmap List',
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: DataTable(
                  columns: const [
                    DataColumn(label: Text('Code')),
                    DataColumn(label: Text('Title')),
                    DataColumn(label: Text('Strand')),
                    DataColumn(label: Text('Mastery Rate'), numeric: true),
                  ],
                  rows: [
                    for (final c in competencies)
                      DataRow(cells: [
                        DataCell(Text('${c['code']}')),
                        DataCell(Text('${c['title']}')),
                        DataCell(Text('${c['strand']}')),
                        DataCell(Text('${c['masteryRate']}%')),
                      ]),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _AttendanceTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final attendanceAsync = ref.watch(_attendanceAnalyticsProvider);

    return attendanceAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => ErrorView(error: e, onRetry: () => ref.refresh(_attendanceAnalyticsProvider)),
      data: (data) {
        final overallRate = (data['overallRate'] as num?)?.toDouble() ?? 0.0;
        final present = data['presentCount'] ?? 0;
        final absent = data['absentCount'] ?? 0;
        final late = data['lateCount'] ?? 0;
        final excused = data['excusedCount'] ?? 0;
        final dailyTrend = (data['dailyTrend'] as List? ?? const []).cast<Map<String, dynamic>>();

        return ListView(
          padding: const EdgeInsets.all(AppSpacing.md),
          children: [
            SectionCard(
              title: 'Overall Attendance Rate: ${overallRate.toStringAsFixed(1)}%',
              child: Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.sm,
                children: [
                  StatusPill(label: 'Present: $present', color: AppColors.tealBrand),
                  StatusPill(label: 'Absent: $absent', color: AppColors.errorLight),
                  StatusPill(label: 'Late: $late', color: AppColors.amber),
                  StatusPill(label: 'Excused: $excused', color: Colors.blueGrey),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            SectionCard(
              title: 'Daily Attendance Trend',
              child: SizedBox(
                height: 160,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    for (final dayData in dailyTrend)
                      Column(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Text('${dayData['rate']}%'),
                          const SizedBox(height: AppSpacing.xs),
                          Container(
                            width: 36,
                            height: ((dayData['rate'] as num?)?.toDouble() ?? 50.0) * 1.0,
                            color: AppColors.tealBrand,
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          Text('${dayData['day']}'),
                        ],
                      ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ExportsTab extends ConsumerWidget {
  const _ExportsTab({
    required this.classGroupId,
    required this.from,
    required this.to,
    required this.onClassChanged,
    required this.onFromChanged,
    required this.onToChanged,
    required this.onExport,
  });

  final String? classGroupId;
  final DateTime from;
  final DateTime to;
  final ValueChanged<String?> onClassChanged;
  final ValueChanged<DateTime> onFromChanged;
  final ValueChanged<DateTime> onToChanged;
  final ValueChanged<String> onExport;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final classes = ref.watch(_classesProvider);

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.md),
      children: [
        SectionCard(
          title: 'Export Attendance Reports',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 12,
                runSpacing: 12,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  SizedBox(
                    width: 220,
                    child: classes.when(
                      loading: () => const LinearProgressIndicator(),
                      error: (e, _) => Text('$e'),
                      data: (rows) => DropdownButtonFormField<String>(
                        value: classGroupId,
                        decoration: InputDecoration(
                          labelText: s.classLabel,
                          border: const OutlineInputBorder(),
                          isDense: true,
                        ),
                        items: [
                          for (final c in rows)
                            DropdownMenuItem(
                              value: '${c['id']}',
                              child: Text('${c['name']}'),
                            ),
                        ],
                        onChanged: onClassChanged,
                      ),
                    ),
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.event, size: 18),
                    label: Text('${s.fromDate} ${ReportsScreen.formatDate(from)}'),
                    onPressed: () async {
                      final picked = await showDatePicker(
                        context: context,
                        initialDate: from,
                        firstDate: DateTime.now().subtract(const Duration(days: 730)),
                        lastDate: DateTime.now().add(const Duration(days: 30)),
                      );
                      if (picked != null) onFromChanged(picked);
                    },
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.event, size: 18),
                    label: Text('${s.toDate} ${ReportsScreen.formatDate(to)}'),
                    onPressed: () async {
                      final picked = await showDatePicker(
                        context: context,
                        initialDate: to,
                        firstDate: DateTime.now().subtract(const Duration(days: 730)),
                        lastDate: DateTime.now().add(const Duration(days: 30)),
                      );
                      if (picked != null) onToChanged(picked);
                    },
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              Wrap(
                spacing: AppSpacing.md,
                children: [
                  OutlinedButton.icon(
                    onPressed: classGroupId == null ? null : () => onExport('csv'),
                    icon: const Icon(Icons.table_view_outlined),
                    label: const Text('Export CSV'),
                  ),
                  OutlinedButton.icon(
                    onPressed: classGroupId == null ? null : () => onExport('pdf'),
                    icon: const Icon(Icons.picture_as_pdf_outlined),
                    label: const Text('Export PDF'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _KpiMetricCard extends StatelessWidget {
  const _KpiMetricCard({
    required this.title,
    required this.value,
    required this.subtitle,
    required this.icon,
    required this.accentColor,
  });

  final String title;
  final String value;
  final String subtitle;
  final IconData icon;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 170,
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: AppColors.slate,
                ),
              ),
              Icon(icon, color: accentColor, size: 20),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            value,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            subtitle,
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.slate,
            ),
          ),
        ],
      ),
    );
  }
}
