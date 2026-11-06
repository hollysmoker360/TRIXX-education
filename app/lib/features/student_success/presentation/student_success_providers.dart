import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/session/session.dart';
import '../../../core/networking/api_client.dart';

/// Student progress profile.
final studentProfileProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/student-success/profiles/$studentId');
  return data as Map<String, dynamic>;
});

/// Risk indicators for a student.
final riskIndicatorsProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get(
    '/api/v1/student-success/indicators',
    query: {'studentId': studentId},
  );
  return (data as List).cast<Map<String, dynamic>>();
});

/// Support referrals for a student.
final supportReferralsProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get(
    '/api/v1/student-success/referrals',
    query: {'studentId': studentId},
  );
  return (data as List).cast<Map<String, dynamic>>();
});

/// Intervention plans for a student.
final interventionPlansProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get(
    '/api/v1/student-success/intervention-plans',
    query: {'studentId': studentId},
  );
  return (data as List).cast<Map<String, dynamic>>();
});

/// Student timeline of events.
final studentTimelineProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/student-success/students/$studentId/timeline');
  return (data as List).cast<Map<String, dynamic>>();
});

/// Create a support referral.
final createReferralProvider = Provider((ref) {
  return (Map<String, dynamic> input) async {
    final api = ref.watch(apiClientProvider);
    final data = await api.post(
      '/api/v1/student-success/referrals',
      data: input,
    );
    return data as Map<String, dynamic>;
  };
});

/// Create an intervention plan.
final createInterventionPlanProvider = Provider((ref) {
  return (Map<String, dynamic> input) async {
    final api = ref.watch(apiClientProvider);
    final data = await api.post(
      '/api/v1/student-success/intervention-plans',
      data: input,
    );
    return data as Map<String, dynamic>;
  };
});

/// Create a progress note.
final createProgressNoteProvider = Provider((ref) {
  return (Map<String, dynamic> input) async {
    final api = ref.watch(apiClientProvider);
    final data = await api.post(
      '/api/v1/student-success/intervention-plans/${input['planId']}/notes',
      data: input,
    );
    return data as Map<String, dynamic>;
  };
});
