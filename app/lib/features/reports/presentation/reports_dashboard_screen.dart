import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Reports & Analytics dashboard — cross-module report catalog.
class ReportsScreen extends ConsumerWidget {
  const ReportsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);

    final definitions = ref.watch(reportDefinitionsProvider);

    return ScreenScaffold(
      title: s.reports,
      description: s.reportsDescription,
      embedded: true,
      child: definitions.when(
        data: (rows) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            ..._categorySections(context, session, s, rows),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(reportDefinitionsProvider)),
      ),
    );
  }

  List<Widget> _categorySections(BuildContext context, SessionState session, S s,
      List<Map<String, dynamic>> rows) {
    final categories = <String, List<Map<String, dynamic>>>{};
    for (final row in rows) {
      final cat = row['category'] as String? ?? 'other';
      categories.putIfAbsent(cat, () => []).add(row);
    }

    final widgets = <Widget>[];
    final categoryOrder = ['academic', 'cbe', 'attendance', 'finance', 'people', 'other'];
    for (final cat in categoryOrder) {
      final reports = categories[cat];
      if (reports == null || reports.isEmpty) continue;
      widgets.add(
        SectionCard(
          title: _categoryTitle(s, cat),
          subtitle: _categorySubtitle(s, cat),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final report in reports)
                _ReportCard(
                  report: report,
                  session: session,
                  s: s,
                ),
            ],
          ),
        ),
      );
    }
    return widgets;
  }

  String _categoryTitle(S s, String cat) {
    switch (cat) {
      case 'academic': return s.academicReports;
      case 'cbe': return s.cbeReports;
      case 'attendance': return s.attendanceReports;
      case 'finance': return s.financeReports;
      case 'people': return s.peopleReports;
      default: return s.otherReports;
    }
  }

  String _categorySubtitle(S s, String cat) {
    switch (cat) {
      case 'academic': return s.academicReportsSubtitle;
      case 'cbe': return s.cbeReportsSubtitle;
      case 'attendance': return s.attendanceReportsSubtitle;
      case 'finance': return s.financeReportsSubtitle;
      case 'people': return s.peopleReportsSubtitle;
      default: return s.otherReportsSubtitle;
    }
  }
}

/// One report card — tapping navigates to the report screen.
class _ReportCard extends StatelessWidget {
  const _ReportCard({
    required this.report,
    required this.session,
    required this.s,
  });

  final Map<String, dynamic> report;
  final SessionState session;
  final S s;

  @override
  Widget build(BuildContext context) {
    final id = report['id'] as String? ?? '';
    final title = report['title'] as String? ?? id;
    final description = report['description'] as String? ?? '';
    final perms = (report['requiredPermissions'] as List? ?? const <String>[])
        .cast<String>();

    final canView = perms.isEmpty ||
        perms.any((p) => session.can(p));

    return Card(
      child: ListTile(
        leading: _IconForReport(id),
        title: Text(title),
        subtitle: Text(description),
        trailing: const Icon(Icons.chevron_right, size: 18),
        enabled: canView,
        onTap: canView ? () => _navigateToReport(context, id) : null,
      ),
    );
  }

  void _navigateToReport(BuildContext context, String id) {
    switch (id) {
      case 'grade-distribution':
        context.push(AppRoutes.reportsGradeDistribution);
      case 'subject-comparison':
        context.push(AppRoutes.reportsSubjectComparison);
      case 'assessment-completion':
        context.push(AppRoutes.reportsAssessmentCompletion);
      case 'term-over-term':
        context.push(AppRoutes.reportsTermOverTerm);
      case 'competency-mastery':
        context.push(AppRoutes.reportsCompetencyMastery);
      case 'evidence-coverage':
        context.push(AppRoutes.reportsEvidenceCoverage);
      case 'attendance-patterns':
        context.push(AppRoutes.reportsAttendancePatterns);
      case 'student-engagement':
        context.push(AppRoutes.reportsStudentEngagement);
      case 'collection-summary':
        context.push(AppRoutes.reportsCollectionSummary);
      case 'expenditure':
        context.push(AppRoutes.reportsExpenditure);
      case 'enrollment':
        context.push(AppRoutes.reportsEnrollment);
      case 'staffing':
        context.push(AppRoutes.reportsStaffing);
      default:
        break;
    }
  }
}

Widget _IconForReport(String id) {
  switch (id) {
    case 'grade-distribution':
      return const Icon(Icons.grade_outlined, color: Colors.blue);
    case 'subject-comparison':
      return const Icon(Icons.compare_outlined, color: Colors.blue);
    case 'assessment-completion':
      return const Icon(Icons.assignment_turned_in_outlined, color: Colors.blue);
    case 'term-over-term':
      return const Icon(Icons.timeline_outlined, color: Colors.blue);
    case 'competency-mastery':
      return const Icon(Icons.track_changes_outlined, color: Colors.green);
    case 'evidence-coverage':
      return const Icon(Icons.visibility_outlined, color: Colors.green);
    case 'attendance-patterns':
      return const Icon(Icons.event_repeat_outlined, color: Colors.orange);
    case 'student-engagement':
      return const Icon(Icons.people_alt_outlined, color: Colors.orange);
    case 'collection-summary':
      return const Icon(Icons.attach_money_outlined, color: Colors.teal);
    case 'expenditure':
      return const Icon(Icons.receipt_long_outlined, color: Colors.teal);
    case 'enrollment':
      return const Icon(Icons.school_outlined, color: Colors.purple);
    case 'staffing':
      return const Icon(Icons.groups_outlined, color: Colors.purple);
    default:
      return const Icon(Icons.insights_outlined);
  }
}
