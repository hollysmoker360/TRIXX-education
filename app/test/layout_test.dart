import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/core/layout/window_class.dart';
import 'package:schoolsphere_app/core/theme/tokens.dart';

/// Layout responds to available width, never to device type
/// (specification section 5.1).
void main() {
  test('window classes follow the Material breakpoints', () {
    expect(windowClassOf(320), WindowClass.compact);
    expect(windowClassOf(599.9), WindowClass.compact);
    expect(windowClassOf(600), WindowClass.medium);
    expect(windowClassOf(839.9), WindowClass.medium);
    expect(windowClassOf(840), WindowClass.expanded);
    expect(windowClassOf(1199.9), WindowClass.expanded);
    expect(windowClassOf(1200), WindowClass.large);
    expect(windowClassOf(2560), WindowClass.large);
  });

  test('navigation switches from the bottom bar to a rail at medium', () {
    expect(WindowClass.compact.usesRail, isFalse);
    expect(WindowClass.medium.usesRail, isTrue);
    expect(WindowClass.expanded.usesRail, isTrue);
    expect(WindowClass.large.usesRail, isTrue);
  });

  test('the rail gains labels only at large', () {
    expect(WindowClass.medium.extendedRail, isFalse);
    expect(WindowClass.expanded.extendedRail, isFalse);
    expect(WindowClass.large.extendedRail, isTrue);
  });

  test('breadcrumbs appear from expanded up', () {
    expect(WindowClass.compact.showsBreadcrumbs, isFalse);
    expect(WindowClass.medium.showsBreadcrumbs, isFalse);
    expect(WindowClass.expanded.showsBreadcrumbs, isTrue);
    expect(WindowClass.large.showsBreadcrumbs, isTrue);
  });

  test('content columns grow with the window class', () {
    expect(WindowClass.compact.contentColumns, 1);
    expect(WindowClass.medium.contentColumns, 1);
    expect(WindowClass.expanded.contentColumns, 2);
    expect(WindowClass.large.contentColumns, 3);
  });

  test('gutters grow with the window class', () {
    expect(WindowClass.compact.gutter, AppSpacing.gutterCompact);
    expect(WindowClass.medium.gutter, AppSpacing.gutterMedium);
    expect(WindowClass.expanded.gutter, AppSpacing.gutterExpanded);
    expect(WindowClass.large.gutter, AppSpacing.gutterExpanded);
  });

  testWidgets('WindowSizeBuilder reads the width it is given, not the screen', (tester) async {
    final seen = <WindowClass>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox(
            width: 500,
            child: WindowSizeBuilder(
              builder: (context, size) {
                seen.add(size);
                return const SizedBox.shrink();
              },
            ),
          ),
        ),
      ),
    );
    expect(seen, [WindowClass.compact]);
  });
}
