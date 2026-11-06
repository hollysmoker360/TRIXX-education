import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/networking/api_client.dart';
import '../../../core/session/session.dart';

/// All report definitions with their categories and required permissions.
final reportDefinitionsProvider = FutureProvider<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/reports/definitions');
  return (data as List).cast<Map<String, dynamic>>();
});

/// Grade distribution report.
final gradeDistributionProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['subjectId'] != null) query['subjectId'] = filters['subjectId'];
  if (filters['from'] != null) query['from'] = filters['from'];
  if (filters['to'] != null) query['to'] = filters['to'];
  return await api.get('/api/v1/reports/academic/grade-distribution', query: query);
});

/// Subject comparison report.
final subjectComparisonProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['from'] != null) query['from'] = filters['from'];
  if (filters['to'] != null) query['to'] = filters['to'];
  return await api.get('/api/v1/reports/academic/subject-comparison', query: query);
});

/// Assessment completion report.
final assessmentCompletionProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['termId'] != null) query['termId'] = filters['termId'];
  return await api.get('/api/v1/reports/academic/assessment-completion', query: query);
});

/// Term over term report.
final termOverTermProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['subjectId'] != null) query['subjectId'] = filters['subjectId'];
  return await api.get('/api/v1/reports/academic/term-over-term', query: query);
});

/// Competency mastery report.
final competencyMasteryProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  return await api.get('/api/v1/reports/cbe/competency-mastery', query: query);
});

/// Evidence coverage report.
final evidenceCoverageProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  return await api.get('/api/v1/reports/cbe/evidence-coverage', query: query);
});

/// Attendance patterns report.
final attendancePatternsProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['from'] != null) query['from'] = filters['from'];
  if (filters['to'] != null) query['to'] = filters['to'];
  return await api.get('/api/v1/reports/attendance/patterns', query: query);
});

/// Student engagement report.
final engagementProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['from'] != null) query['from'] = filters['from'];
  if (filters['to'] != null) query['to'] = filters['to'];
  return await api.get('/api/v1/reports/attendance/engagement', query: query);
});

/// Fee collection summary report.
final collectionSummaryProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['from'] != null) query['from'] = filters['from'];
  if (filters['to'] != null) query['to'] = filters['to'];
  return await api.get('/api/v1/reports/finance/collection-summary', query: query);
});

/// Expenditure report.
final expenditureProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['from'] != null) query['from'] = filters['from'];
  if (filters['to'] != null) query['to'] = filters['to'];
  return await api.get('/api/v1/reports/finance/expenditure', query: query);
});

/// Enrollment report.
final enrollmentProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['asOf'] != null) query['asOf'] = filters['asOf'];
  return await api.get('/api/v1/reports/people/enrollment', query: query);
});

/// Staffing report.
final staffingProvider = FutureProvider.autoDispose((ref) async {
  final api = ref.watch(apiClientProvider);
  return await api.get('/api/v1/reports/people/staffing');
});
