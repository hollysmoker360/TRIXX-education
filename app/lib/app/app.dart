import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/brand/brand.dart';
import '../core/l10n/strings.dart';
import '../core/settings/preferences.dart';
import '../core/sync/sync_status_provider.dart';
import '../core/theme/app_theme.dart';
import 'router.dart';

/// Root widget: wires the theme (light and dark, per the user's preference),
/// the string catalogue, and the router.
class TrixxApp extends ConsumerWidget {
  const TrixxApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(localeProvider);
    final strings = locale.languageCode == 'sw' ? S.sw : S.en;
    final themeMode = ref.watch(themeModeProvider);
    ref.watch(autoSyncProvider);

    return LocaleScope(
      strings: strings,
      child: MaterialApp.router(
        title: AppBrand.name,
        debugShowCheckedModeBanner: false,
        theme: buildTheme(Brightness.light),
        darkTheme: buildTheme(Brightness.dark),
        themeMode: themeMode,
        locale: locale,
        supportedLocales: const [Locale('en'), Locale('sw')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        routerConfig: ref.watch(appRouterProvider),
        scrollBehavior: const _AppScrollBehavior(),
      ),
    );
  }
}

/// Desktop and web users expect to drag with a mouse and see a scrollbar.
class _AppScrollBehavior extends MaterialScrollBehavior {
  const _AppScrollBehavior();

  @override
  Set<PointerDeviceKind> get dragDevices => {
        PointerDeviceKind.touch,
        PointerDeviceKind.mouse,
        PointerDeviceKind.trackpad,
        PointerDeviceKind.stylus,
      };
}
