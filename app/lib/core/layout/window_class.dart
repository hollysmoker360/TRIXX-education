import 'package:flutter/widgets.dart';

import '../theme/tokens.dart';

/// Material window size classes (specification section 5.1). Layouts respond to
/// available width, never to device type.
enum WindowClass { compact, medium, expanded, large }

WindowClass windowClassOf(double width) {
  if (width < 600) return WindowClass.compact;
  if (width < 840) return WindowClass.medium;
  if (width < 1200) return WindowClass.expanded;
  return WindowClass.large;
}

extension WindowClassX on WindowClass {
  bool get isCompact => this == WindowClass.compact;
  bool get isMedium => this == WindowClass.medium;
  bool get isExpanded => this == WindowClass.expanded;
  bool get isLarge => this == WindowClass.large;

  /// Rail or sidebar navigation instead of the bottom bar.
  bool get usesRail => index >= WindowClass.medium.index;

  /// Sidebar is extended (labelled) at this class.
  bool get extendedRail => this == WindowClass.large;

  /// Breadcrumbs and multi-column dashboards are available here and above.
  bool get showsBreadcrumbs => index >= WindowClass.expanded.index;

  /// Two-or-more-column content layouts.
  int get contentColumns => switch (this) {
        WindowClass.compact => 1,
        WindowClass.medium => 1,
        WindowClass.expanded => 2,
        WindowClass.large => 3,
      };

  double get gutter => switch (this) {
        WindowClass.compact => AppSpacing.gutterCompact,
        WindowClass.medium => AppSpacing.gutterMedium,
        _ => AppSpacing.gutterExpanded,
      };
}

/// Reads the current window class from the [MediaQuery] width.
WindowClass windowClassOfContext(BuildContext context) =>
    windowClassOf(MediaQuery.sizeOf(context).width);

/// A [LayoutBuilder]-based helper for widgets that need the class without
/// depending on the full screen width (for example a panel inside a sidebar).
class WindowSizeBuilder extends StatelessWidget {
  const WindowSizeBuilder({super.key, required this.builder});

  final Widget Function(BuildContext context, WindowClass size) builder;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) => builder(context, windowClassOf(constraints.maxWidth)),
    );
  }
}
