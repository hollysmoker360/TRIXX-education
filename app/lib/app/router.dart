import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/router/app_routes.dart';
import '../core/session/session.dart';
import '../core/widgets/common.dart';
import '../features/assessment/presentation/marks_entry_screen.dart';
import '../features/assessment/presentation/report_card_screen.dart';
import '../features/attendance/presentation/attendance_screen.dart';
import '../features/audit/presentation/audit_screen.dart';
import '../features/auth/presentation/accept_invitation_screen.dart';
import '../features/auth/presentation/login_screen.dart';
import '../features/auth/presentation/mfa_screen.dart';
import '../features/auth/presentation/reset_password_screen.dart';
import '../features/auth/presentation/school_select_screen.dart';
import '../features/auth/presentation/session_expired_screen.dart';
import '../features/super_admin/presentation/super_admin_screen.dart';
import '../features/chat/presentation/chat_screen.dart';
import '../features/gallery/presentation/component_gallery_screen.dart';
import '../features/home/presentation/home_shell.dart';
import '../features/notifications/presentation/notifications_screen.dart';
import '../features/people/presentation/student_profile_screen.dart';
import '../features/quizzes/presentation/quizzes_screen.dart';

export '../core/router/app_routes.dart';

/// Declarative navigation ([ADR-006]).
///
/// A single auth-gated root ([HomeShell]) plus path-based sub-navigation. The
/// provider *watches* the session so the router rebuilds whenever auth state
/// changes; [GoRouter.redirect] keeps unauthenticated users on the sign-in
/// route, sends signed-in users with no active school to the school picker, and
/// keeps signed-in users away from sign-in. Route-level permissions are
/// enforced by the pages themselves (and always by the backend); hiding a route
/// here is only a presentation concern.
final appRouterProvider = Provider<GoRouter>((ref) {
  final session = ref.watch(sessionProvider);
  final hasActiveMembership = session.memberships.any((m) => m.status == 'active');

  return GoRouter(
    initialLocation: AppRoutes.home,
    errorBuilder: (context, state) => Scaffold(
      body: NotFoundView(onBack: () => context.go(AppRoutes.home)),
    ),
    redirect: (context, state) {
      final location = state.matchedLocation;
      final atAuth = location == AppRoutes.login ||
          location == AppRoutes.acceptInvitation ||
          location == AppRoutes.resetPassword ||
          location == AppRoutes.mfa ||
          location == AppRoutes.sessionExpired;

      if (session.initializing) return null;
      if (!session.isSignedIn) return atAuth ? null : AppRoutes.login;
      if (location == AppRoutes.login || location == AppRoutes.resetPassword) {
        return hasActiveMembership ? AppRoutes.home : AppRoutes.selectSchool;
      }
      if (!hasActiveMembership && location != AppRoutes.selectSchool) {
        return AppRoutes.selectSchool;
      }
      return null;
    },
    routes: [
      GoRoute(path: AppRoutes.login, builder: (context, state) => const LoginScreen()),
      GoRoute(
        path: AppRoutes.acceptInvitation,
        builder: (context, state) => AcceptInvitationScreen(
          token: state.uri.queryParameters['token'],
        ),
      ),
      GoRoute(
        path: AppRoutes.superAdmin,
        builder: (context, state) => const SuperAdminScreen(),
      ),
      GoRoute(path: AppRoutes.resetPassword, builder: (context, state) => const ResetPasswordScreen()),
      GoRoute(path: AppRoutes.mfa, builder: (context, state) => const MfaScreen()),
      GoRoute(path: AppRoutes.sessionExpired, builder: (context, state) => const SessionExpiredScreen()),
      GoRoute(
        path: AppRoutes.selectSchool,
        builder: (context, state) => const SchoolSelectScreen(),
      ),
      GoRoute(
        path: AppRoutes.home,
        builder: (context, state) {
          if (session.initializing) {
            return const Scaffold(body: Center(child: CircularProgressIndicator()));
          }
          if (!session.isSignedIn) return const LoginScreen();
          return HomeShell(
            key: ValueKey((session.user!.id, session.selectedSchoolId)),
          );
        },
      ),
      GoRoute(path: AppRoutes.notifications, builder: (context, state) => const NotificationsScreen()),
      GoRoute(path: AppRoutes.gallery, builder: (context, state) => const ComponentGalleryScreen()),
      GoRoute(path: AppRoutes.audit, builder: (context, state) => const AuditScreen()),
      GoRoute(
        path: AppRoutes.studentProfile,
        builder: (context, state) => StudentProfileScreen(
          studentId: state.pathParameters['studentId']!,
          initialName: state.extra is String ? state.extra as String : null,
        ),
        routes: [
          GoRoute(
            path: AppRoutes.reportCard,
            builder: (context, state) => ReportCardScreen(
              studentId: state.pathParameters['studentId']!,
              studentName: state.extra is String ? state.extra as String : null,
            ),
          ),
        ],
      ),
      GoRoute(
        path: AppRoutes.marksEntry,
        builder: (context, state) => MarksEntryScreen(
          assessmentId: state.pathParameters['assessmentId']!,
          assessmentName: state.extra is String ? state.extra as String : null,
        ),
      ),
      GoRoute(
        path: AppRoutes.chatRoom,
        builder: (context, state) => ChannelScreen(
          channelId: state.pathParameters['channelId']!,
          channelName: state.extra is String ? state.extra as String : '',
        ),
      ),
      GoRoute(
        path: AppRoutes.quizAttempt,
        builder: (context, state) => QuizAttemptScreen(
          quizId: state.pathParameters['quizId']!,
          start: (state.extra as Map<String, dynamic>?) ?? const <String, dynamic>{},
        ),
      ),
      GoRoute(
        path: AppRoutes.attendanceRegister,
        builder: (context, state) => RegisterScreen(
          sessionId: state.pathParameters['sessionId']!,
        ),
      ),
    ],
  );
});
