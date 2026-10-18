import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/features/reports/presentation/reports_screen.dart';

void main() {
  testWidgets('ReportsScreen renders tab bar with all 5 analytics tabs', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(
          home: Scaffold(
            body: ReportsScreen(),
          ),
        ),
      ),
    );

    expect(find.text('Overview'), findsOneWidget);
    expect(find.text('Academic Performance'), findsOneWidget);
    expect(find.text('CBE Mastery'), findsOneWidget);
    expect(find.text('Attendance'), findsOneWidget);
    expect(find.text('Exports'), findsOneWidget);
  });

  testWidgets('Tapping tab switches view', (tester) async {
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(
          home: Scaffold(
            body: ReportsScreen(),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Exports'));
    await tester.pumpAndSettle();

    expect(find.text('Export Attendance Reports'), findsOneWidget);
  });
}
