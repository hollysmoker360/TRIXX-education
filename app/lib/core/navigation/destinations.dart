import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Stable identifiers for shell destinations.
///
/// The shell keeps its pages in a lazy `IndexedStack` rather than giving each
/// one a route, so "go to the timetable" is not a `context.go(...)`. Screens
/// that want to move the user — the landing-page hero, an empty state, a
/// dashboard tile — set [shellDestinationProvider] and the shell switches.
abstract final class ShellDestination {
  static const today = 'today';
  static const overview = 'overview';
  static const family = 'family';
  static const timetable = 'timetable';
  static const attendance = 'attendance';
  static const assignments = 'assignments';
  static const quizzes = 'quizzes';
  static const marks = 'marks';
  static const chat = 'chat';
  static const people = 'people';
  static const academics = 'academics';
  static const cbe = 'cbe';
  static const elearning = 'elearning';
  static const examinations = 'examinations';
  static const reports = 'reports';
  static const resources = 'resources';
  static const tasks = 'tasks';
}

/// One-shot request for the shell to focus a destination.
///
/// The shell consumes the value (reads it, then resets it to null) so the same
/// destination can be requested twice in a row. A plain provider rather than a
/// notifier keeps the contract obvious: write a destination id, the shell acts.
final shellDestinationProvider = StateProvider<String?>((ref) => null);

/// Requests that the shell moves to [destination], if that destination exists
/// for the signed-in user.
void goToShellDestination(WidgetRef ref, String destination) {
  ref.read(shellDestinationProvider.notifier).state = destination;
}
