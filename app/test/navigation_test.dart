import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:schoolsphere_app/core/layout/adaptive_shell.dart';
import 'package:schoolsphere_app/core/networking/token_store.dart';
import 'package:schoolsphere_app/core/session/session.dart';
import 'package:schoolsphere_app/core/theme/app_theme.dart';
import 'package:schoolsphere_app/core/widgets/offline.dart';

/// The sidebar groups destinations under section headings (sidebar
/// organisation in the dashboard feature specification).
void main() {
  final items = [
    for (final (label, group) in [
      ('Today', 'Workspace'),
      ('Tasks', 'Workspace'),
      ('Timetable', 'Teaching & learning'),
      ('Boarding', 'School operations'),
      ('Settings', 'Administration'),
    ])
      NavItem(label: label, icon: Icons.circle_outlined, group: group, page: Text('$label page')),
  ];

  Future<void> pumpShell(WidgetTester tester, Size size, {ValueChanged<int>? onSelected}) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    final tokens = await tester.runAsync(TokenStore.create);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(tokens!),
          connectivityProvider.overrideWith((ref) => Stream.value([ConnectivityResult.wifi])),
        ],
        child: MaterialApp(
          theme: buildTheme(Brightness.light),
          home: AdaptiveShell(items: items, selectedIndex: 0, onSelected: onSelected ?? (_) {}),
        ),
      ),
    );
    await tester.pump();
  }

  testWidgets('the extended sidebar shows each group heading once', (tester) async {
    await pumpShell(tester, const Size(1400, 900));
    for (final heading in ['WORKSPACE', 'TEACHING & LEARNING', 'SCHOOL OPERATIONS', 'ADMINISTRATION']) {
      expect(find.text(heading), findsOneWidget);
    }
    expect(find.byType(NavigationRail), findsNothing);
  });

  testWidgets('tapping a sidebar entry selects it', (tester) async {
    int? selected;
    await pumpShell(tester, const Size(1400, 900), onSelected: (i) => selected = i);
    await tester.tap(find.widgetWithText(ListTile, 'Boarding'));
    expect(selected, 3);
  });

  testWidgets('the compact rail keeps icons and labels without headings', (tester) async {
    await pumpShell(tester, const Size(900, 900));
    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.text('WORKSPACE'), findsNothing);
  });
}
