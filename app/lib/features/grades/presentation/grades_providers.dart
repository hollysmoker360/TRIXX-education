import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/session/session.dart';

/// Fetches assessments for a class/term/subject.
final assessmentsProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classGroupId'] != null) query['classGroupId'] = filters['classGroupId'];
  if (filters['subjectId'] != null) query['subjectId'] = filters['subjectId'];
  if (filters['termId'] != null) query['termId'] = filters['termId'];
  if (filters['published'] != null) query['published'] = filters['published'];
  final data = await api.get('/api/v1/grades/assessments', query: query);
  return (data as List).cast<Map<String, dynamic>>();
});

/// Fetches grade entries for a single assessment.
final gradeEntriesProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, assessmentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/grades/assessments/$assessmentId/entries');
  return (data as List).cast<Map<String, dynamic>>();
});

/// Fetches the full gradebook grid for a class.
final classGradebookProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, Map<String, dynamic>>((ref, filters) async {
  final api = ref.watch(apiClientProvider);
  final query = <String, dynamic>{};
  if (filters['classId'] != null) query['classId'] = filters['classId'];
  if (filters['termId'] != null) query['termId'] = filters['termId'];
  final data =
      await api.get('/api/v1/grades/classes/${filters['classId']}/gradebook', query: query);
  return (data as List).cast<Map<String, dynamic>>();
});

/// Fetches grade bands for the school.
final gradeBandsProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/grades/bands');
  return (data as List).cast<Map<String, dynamic>>();
});

/// Fetches a student's own grades.
final studentGradesProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/grades/students/$studentId/grades');
  return (data as List).cast<Map<String, dynamic>>();
});
