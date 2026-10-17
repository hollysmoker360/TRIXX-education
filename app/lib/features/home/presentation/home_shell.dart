import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/adaptive_shell.dart';
import '../../../core/layout/command_palette.dart';
import '../../../core/navigation/destinations.dart';
import '../../../core/router/app_routes.dart';
import '../../../core/session/session.dart';
import '../../academics/presentation/academics_screen.dart';
import '../../cbe/presentation/cbe_screen.dart';
import '../../assessment/presentation/assessments_screen.dart';
import '../../assignments/presentation/assignments_screen.dart';
import '../../attendance/presentation/attendance_screen.dart';
import '../../audit/presentation/audit_screen.dart';
import '../../chat/presentation/chat_screen.dart';
import '../../dashboard/presentation/parent_family_screen.dart';
import '../../dashboard/presentation/today_screen.dart';
import '../../gallery/presentation/component_gallery_screen.dart';
import '../../lessons/presentation/lessons_screen.dart';
import '../../notifications/presentation/notifications_provider.dart';
import '../../notifications/presentation/notifications_screen.dart';
import '../../overview/presentation/overview_screen.dart';
import '../../people/presentation/people_screen.dart';
import '../../quizzes/presentation/quizzes_screen.dart';
import '../../reports/presentation/reports_screen.dart';
import '../../resources/presentation/resources_screen.dart';
import '../../settings/presentation/settings_screen.dart';
import '../../subscriptions/presentation/subscriptions_screen.dart';
import '../../sync/presentation/sync_screen.dart';
import '../../tasks/presentation/tasks_screen.dart';
import '../../timetable/presentation/timetable_screen.dart';

/// The signed-in shell. Destinations are permission-filtered (specification
/// section 5.3); a role that cannot use an entry never sees it. The backend
/// still enforces every permission.
class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  int _index = 0;

  List<NavItem> _destinations(SessionState session, S s) {
    final can = session.can;
    final roles = session.membership?.roles.toSet() ?? <String>{};
    final isLearner = roles.contains('student');
    final isTeacher = roles.intersection({'teacher', 'dept_head', 'principal', 'admin', 'owner'}).isNotEmpty;
    final isGuardian = roles.contains('parent');

    final items = <NavItem>[
      if (isTeacher || isLearner)
        NavItem(
          id: ShellDestination.today,
          label: s.today,
          icon: Icons.today_outlined,
          selectedIcon: Icons.today,
          group: s.today,
          keywords: const ['today', 'dashboard', 'leo'],
          page: const TodayScreen(),
        ),
      NavItem(
        id: ShellDestination.overview,
        label: s.overview,
        icon: Icons.dashboard_outlined,
        selectedIcon: Icons.dashboard,
        group: s.today,
        keywords: const ['overview', 'dashboard'],
        page: const OverviewScreen(),
      ),
      if (isGuardian)
        NavItem(
          id: ShellDestination.family,
          label: s.family,
          icon: Icons.family_restroom_outlined,
          selectedIcon: Icons.family_restroom,
          group: s.today,
          keywords: const ['family', 'children', 'guardian'],
          page: const ParentFamilyScreen(),
        ),
      if (can('timetable.view'))
        NavItem(
          id: ShellDestination.timetable,
          label: s.timetable,
          icon: Icons.calendar_view_week_outlined,
          selectedIcon: Icons.calendar_view_week,
          group: s.academics,
          keywords: const ['timetable', 'schedule', 'ratiba'],
          page: const TimetableScreen(),
        ),
      if (can('attendance.take'))
        NavItem(
          id: ShellDestination.attendance,
          label: s.attendance,
          icon: Icons.fact_check_outlined,
          selectedIcon: Icons.fact_check,
          group: s.academics,
          keywords: const ['attendance', 'register', 'mahudhurio'],
          page: const AttendanceScreen(),
        ),
      if (can('assignment.create') || can('assignment.submit'))
        NavItem(
          id: ShellDestination.assignments,
          label: s.assignments,
          icon: Icons.assignment_outlined,
          selectedIcon: Icons.assignment,
          group: s.academics,
          keywords: const ['assignments', 'homework'],
          page: const AssignmentsScreen(),
        ),
      if (can('assignment.create') || can('assignment.submit'))
        NavItem(
          id: ShellDestination.quizzes,
          label: s.quizzes,
          icon: Icons.quiz_outlined,
          selectedIcon: Icons.quiz,
          group: s.academics,
          keywords: const ['quizzes', 'exams', 'tests'],
          page: const QuizzesScreen(),
        ),
      if (can('grade.enter') || can('grade.view.own') || can('assignment.grade'))
        NavItem(
          id: ShellDestination.marks,
          label: s.marks,
          icon: Icons.grading_outlined,
          selectedIcon: Icons.grading,
          group: s.academics,
          keywords: const ['marks', 'grades', 'results', 'alama'],
          page: const AssessmentsScreen(),
        ),
      if (can('tasks.own'))
        NavItem(
          id: ShellDestination.tasks,
          label: s.tasks,
          icon: Icons.checklist_outlined,
          selectedIcon: Icons.checklist,
          group: s.academics,
          keywords: const ['tasks', 'to do'],
          page: const TasksScreen(),
        ),
      if (can('academics.view'))
        NavItem(
          id: ShellDestination.academics,
          label: s.academics,
          icon: Icons.school_outlined,
          selectedIcon: Icons.school,
          group: s.academics,
          keywords: const ['classes', 'subjects', 'years', 'terms'],
          page: const AcademicsScreen(),
        ),
      if (can('academics.view'))
        NavItem(
          id: ShellDestination.cbe,
          label: 'CBE',
          icon: Icons.track_changes_outlined,
          selectedIcon: Icons.track_changes,
          group: s.academics,
          keywords: const ['cbe', 'competencies', 'curriculum', 'rubrics'],
          page: const CbeScreen(),
        ),
      if (can('attendance.report'))
        NavItem(
          id: ShellDestination.reports,
          label: s.reports,
          icon: Icons.insights_outlined,
          selectedIcon: Icons.insights,
          group: s.academics,
          keywords: const ['reports', 'analytics'],
          page: const ReportsScreen(),
        ),
      if (can('learning.view'))
        NavItem(
          id: ShellDestination.resources,
          label: s.resources,
          icon: Icons.menu_book_outlined,
          selectedIcon: Icons.menu_book,
          group: s.learn,
          keywords: const ['learning hub', 'resources', 'materials'],
          page: const ResourcesScreen(),
        ),
      if (can('learning.view'))
        NavItem(
          label: s.lessons,
          icon: Icons.video_library_outlined,
          selectedIcon: Icons.video_library,
          group: s.learn,
          keywords: const ['lessons', 'online', 'live'],
          page: const LessonsScreen(),
        ),
      if (can('chat.use'))
        NavItem(
          id: ShellDestination.chat,
          label: s.messages,
          icon: Icons.forum_outlined,
          selectedIcon: Icons.forum,
          group: s.messages,
          keywords: const ['chat', 'messages', 'channels'],
          page: const ChatScreen(),
        ),
      if (can('people.view'))
        NavItem(
          id: ShellDestination.people,
          label: s.people,
          icon: Icons.groups_outlined,
          selectedIcon: Icons.groups,
          group: s.people,
          keywords: const ['students', 'staff', 'people', 'directory'],
          page: const PeopleScreen(),
        ),
      NavItem(
        label: s.notifications,
        icon: Icons.notifications_none,
        selectedIcon: Icons.notifications,
        group: s.people,
        keywords: const ['notifications', 'alerts', 'arifa'],
        page: const NotificationsScreen(embedded: true),
      ),
      if (can('subscription.manage'))
        NavItem(
          label: s.subscription,
          icon: Icons.payment_outlined,
          selectedIcon: Icons.payment,
          group: s.settings,
          keywords: const ['subscription', 'billing', 'plan'],
          page: const SubscriptionsScreen(),
        ),
      if (can('audit.view'))
        NavItem(
          label: s.audit,
          icon: Icons.history_outlined,
          selectedIcon: Icons.history,
          group: s.settings,
          keywords: const ['audit', 'log', 'history'],
          page: const AuditScreen(),
        ),
      NavItem(
        label: s.syncCenter,
        icon: Icons.sync_outlined,
        selectedIcon: Icons.sync,
        group: s.settings,
        keywords: const ['sync', 'offline', 'usawazishaji'],
        page: const SyncScreen(),
      ),
      NavItem(
        label: s.settings,
        icon: Icons.settings_outlined,
        selectedIcon: Icons.settings,
        group: s.settings,
        keywords: const ['settings', 'preferences'],
        page: const SettingsScreen(),
      ),
      if (kDebugMode)
        NavItem(
          label: s.componentGallery,
          icon: Icons.widgets_outlined,
          selectedIcon: Icons.widgets,
          group: s.settings,
          keywords: const ['gallery', 'components', 'design system'],
          page: const ComponentGalleryScreen(),
        ),
    ];
    return items;
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<SessionState>(sessionProvider, (previous, next) {
      if (!next.isSignedIn) context.go(AppRoutes.login);
    });

    // A screen inside the shell may ask to focus a destination (the
    // landing-page hero, an empty state, a dashboard tile). One-shot.
    ref.listen<String?>(shellDestinationProvider, (previous, requested) {
      if (requested == null) return;
      final items = _destinations(ref.read(sessionProvider), S.of(context));
      final target = items.indexWhere((item) => item.id == requested);
      ref.read(shellDestinationProvider.notifier).state = null;
      if (target >= 0 && mounted) setState(() => _index = target);
    });

    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final unread = ref.watch(unreadNotificationsProvider);
    final destinations = _destinations(session, s);
    final index = _index.clamp(0, destinations.length - 1);

    if (session.membership == null) {
      return Scaffold(
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.info_outline, size: 44),
                const SizedBox(height: 12),
                Text(s.notAMember, textAlign: TextAlign.center),
                const SizedBox(height: 16),
                OutlinedButton(
                  onPressed: () => ref.read(sessionProvider.notifier).signOut(),
                  child: Text(s.signOut),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return AdaptiveShell(
      items: destinations,
      selectedIndex: index,
      onSelected: (i) => setState(() => _index = i),
      title: destinations[index].label,
      notificationCount: unread,
      onNotifications: () => context.push(AppRoutes.notifications),
      schoolName: session.membership?.schoolName,
      userName: session.user?.displayName,
      extraCommands: [
        if (session.can('audit.view'))
          CommandItem(
            label: s.audit,
            icon: Icons.history_outlined,
            keywords: const ['audit', 'log'],
            onSelect: () => context.push(AppRoutes.audit),
          ),
        CommandItem(
          label: s.componentGallery,
          icon: Icons.widgets_outlined,
          keywords: const ['gallery', 'components'],
          onSelect: () => context.push(AppRoutes.gallery),
        ),
      ],
    );
  }
}
