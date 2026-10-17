import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/core/brand/brand.dart';
import 'package:schoolsphere_app/core/theme/app_theme.dart';
import 'package:schoolsphere_app/core/theme/tokens.dart';
import 'package:schoolsphere_app/core/widgets/common.dart';

/// Behaviour that the component library promises, exercised in both themes
/// (specification sections 7.2, 8 and 13.1).
void main() {
  Widget host(Widget child, {Brightness brightness = Brightness.light, double width = 1024}) =>
      ProviderScope(
        child: MaterialApp(
          theme: buildTheme(brightness),
          home: Scaffold(
            body: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(width: width, height: 600, child: child),
            ),
          ),
        ),
      );

  group('states', () {
    testWidgets('a loading view shows the skeleton, not a blank screen', (tester) async {
      await tester.pumpWidget(
        host(
          AsyncView<List<String>>(
            value: const AsyncValue.loading(),
            onRetry: () {},
            builder: (rows) => Text('${rows.length} rows'),
          ),
        ),
      );
      expect(find.byType(LoadingSkeleton), findsOneWidget);
    });

    testWidgets('data replaces the skeleton once it arrives', (tester) async {
      await tester.pumpWidget(
        host(
          AsyncView<List<String>>(
            value: const AsyncValue.data(['a', 'b', 'c']),
            onRetry: () {},
            builder: (rows) => Text('${rows.length} rows'),
          ),
        ),
      );
      expect(find.byType(LoadingSkeleton), findsNothing);
      expect(find.text('3 rows'), findsOneWidget);
    });

    testWidgets('an empty result invites an action rather than showing a table', (tester) async {
      await tester.pumpWidget(
        host(
          AsyncView<List<String>>(
            value: const AsyncValue.data([]),
            onRetry: () {},
            isEmpty: (rows) => rows.isEmpty,
            emptyMessage: 'Nothing here yet',
            builder: (rows) => Text('${rows.length} rows'),
          ),
        ),
      );
      expect(find.text('Nothing here yet'), findsOneWidget);
      expect(find.text('0 rows'), findsNothing);
    });

    testWidgets('an error keeps the retry affordance', (tester) async {
      await tester.pumpWidget(
        host(
          AsyncView<List<String>>(
            value: AsyncValue.error(Exception('offline'), StackTrace.empty),
            onRetry: () {},
            builder: (rows) => Text('${rows.length} rows'),
          ),
        ),
      );
      expect(find.byType(ErrorView), findsOneWidget);
    });
  });

  group('data', () {
    testWidgets('a table renders its rows by rowKey', (tester) async {
      await tester.pumpWidget(
        host(
          AppDataTable<Map<String, String>>(
            columns: [
              AppColumn(label: 'Name', primary: true, cell: (context, row) => Text(row['name']!)),
              AppColumn(label: 'Class', cell: (context, row) => Text(row['class']!)),
            ],
            rows: [
              {'name': 'Amina Yusuf', 'class': 'Grade 8 East'},
              {'name': 'Brian Ochieng', 'class': 'Grade 8 West'},
            ],
            rowKey: (row) => row['name']!,
          ),
        ),
      );
      expect(find.text('Amina Yusuf'), findsOneWidget);
      expect(find.text('Brian Ochieng'), findsOneWidget);
    });

    testWidgets('sensitive columns are masked in list rows by default', (tester) async {
      await tester.pumpWidget(
        host(
          AppDataTable<Map<String, String>>(
            columns: [
              AppColumn(label: 'Name', primary: true, cell: (context, row) => Text(row['name']!)),
              AppColumn(
                label: 'Phone',
                sensitive: true,
                cell: (context, row) => Text(row['phone']!),
              ),
            ],
            rows: [
              {'name': 'Amina Yusuf', 'phone': '+254712345678'},
            ],
            rowKey: (row) => row['name']!,
          ),
        ),
      );
      expect(find.text('+254712345678'), findsNothing);
      expect(find.text('Name'), findsWidgets);
    });

    testWidgets('sensitive columns can be revealed when explicitly requested', (tester) async {
      await tester.pumpWidget(
        host(
          AppDataTable<Map<String, String>>(
            maskSensitive: false,
            columns: [
              AppColumn(label: 'Name', primary: true, cell: (context, row) => Text(row['name']!)),
              AppColumn(
                label: 'Phone',
                sensitive: true,
                cell: (context, row) => Text(row['phone']!),
              ),
            ],
            rows: [
              {'name': 'Amina Yusuf', 'phone': '+254712345678'},
            ],
            rowKey: (row) => row['name']!,
          ),
        ),
      );
      expect(find.text('+254712345678'), findsOneWidget);
    });
  });

  group('surfaces and inputs', () {
    testWidgets('a status chip pairs colour with an icon and a label', (tester) async {
      await tester.pumpWidget(
        host(
          const StatusChip(
            label: 'Needs review',
            kind: StatusKind.warning,
            icon: Icons.warning_amber_outlined,
          ),
        ),
      );
      expect(find.text('Needs review'), findsOneWidget);
      expect(find.byIcon(Icons.warning_amber_outlined), findsOneWidget);
    });

    testWidgets('a banner announces itself to assistive technology', (tester) async {
      await tester.pumpWidget(
        host(const AppBanner(kind: StatusKind.warning, message: 'You are offline')),
      );
      expect(find.text('You are offline'), findsOneWidget);
    });

    testWidgets('a metric card shows label, value and period', (tester) async {
      await tester.pumpWidget(
        host(
          const MetricCard(
            label: 'Enrolled students',
            value: '1,284',
            period: '+3.2% vs last term',
            trend: MetricTrend.up,
          ),
        ),
      );
      expect(find.text('Enrolled students'), findsOneWidget);
      expect(find.text('1,284'), findsOneWidget);
      expect(find.text('+3.2% vs last term'), findsOneWidget);
    });

    testWidgets('a busy button blocks a second tap', (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        host(AppButton(label: 'Save', busy: true, onPressed: () => taps++)),
      );
      await tester.tap(find.byType(AppButton));
      await tester.pump();
      expect(taps, 0);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });

    testWidgets('the minimum touch target is respected', (tester) async {
      await tester.pumpWidget(host(AppButton(label: 'Continue', onPressed: () {})));
      final size = tester.getSize(find.byType(AppButton));
      expect(size.height, greaterThanOrEqualTo(AppLayout.minTouchTarget));
    });
  });

  group('brand', () {
    testWidgets('the mark replaces the lockup below the minimum width', (tester) async {
      await tester.pumpWidget(host(const TrixxLogo(height: 24)));
      final svg = tester.widget<SvgPicture>(find.byType(SvgPicture));
      expect((svg.bytesLoader as SvgAssetLoader).assetName, AppBrand.mark);
    });

    testWidgets('the full lockup is used at normal sizes', (tester) async {
      await tester.pumpWidget(host(const TrixxLogo(height: 64)));
      final svg = tester.widget<SvgPicture>(find.byType(SvgPicture));
      expect((svg.bytesLoader as SvgAssetLoader).assetName, AppBrand.logoHorizontal);
    });

    testWidgets('the dark theme gets the dark lockup', (tester) async {
      await tester.pumpWidget(host(const TrixxLogo(height: 64), brightness: Brightness.dark));
      final svg = tester.widget<SvgPicture>(find.byType(SvgPicture));
      expect((svg.bytesLoader as SvgAssetLoader).assetName, AppBrand.logoHorizontalDark);
    });

    testWidgets('an avatar falls back to initials when there is no image', (tester) async {
      await tester.pumpWidget(host(const Avatar(name: 'Grace Wanjiru')));
      expect(find.text('GW'), findsOneWidget);
    });

    testWidgets('a one-word name produces a one-letter avatar', (tester) async {
      await tester.pumpWidget(host(const Avatar(name: 'Kilimani', square: true)));
      expect(find.text('K'), findsOneWidget);
    });
  });
}
