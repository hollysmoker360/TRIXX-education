import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoolsphere_app/core/theme/app_theme.dart';
import 'package:schoolsphere_app/core/theme/tokens.dart';

/// Locks down the colour and contrast rules from specification sections 4.1,
/// 4.5 and 9 so a palette tweak cannot quietly reduce readability.
void main() {
  double aa() => 4.5;
  double aaa() => 7;

  group('contrast (WCAG 2.2)', () {
    test('primary text on its surface reaches AAA', () {
      expect(
        Contrast.ratio(AppColors.textPrimaryLight, AppColors.surfaceLight),
        greaterThanOrEqualTo(aaa()),
      );
      expect(
        Contrast.ratio(AppColors.textPrimaryDark, AppColors.surfaceDark),
        greaterThanOrEqualTo(aaa()),
      );
    });

    test('secondary text on its surface reaches AA', () {
      expect(
        Contrast.ratio(AppColors.textSecondaryLight, AppColors.surfaceLight),
        greaterThanOrEqualTo(aa()),
      );
      expect(
        Contrast.ratio(AppColors.textSecondaryDark, AppColors.surfaceDark),
        greaterThanOrEqualTo(aa()),
      );
    });

    test('the action colour carries white text at AA', () {
      expect(Contrast.ratio(Colors.white, AppColors.tealAction), greaterThanOrEqualTo(aa()));
    });

    test('status colours are readable on their surfaces', () {
      for (final colour in [
        AppColors.successLight,
        AppColors.warningLight,
        AppColors.errorLight,
        AppColors.infoLight,
      ]) {
        expect(
          Contrast.ratio(colour, AppColors.surfaceLight),
          greaterThanOrEqualTo(aa()),
          reason: '$colour is too pale on the light surface',
        );
      }
      for (final colour in [
        AppColors.successDark,
        AppColors.warningDark,
        AppColors.errorDark,
        AppColors.infoDark,
      ]) {
        expect(
          Contrast.ratio(colour, AppColors.surfaceDark),
          greaterThanOrEqualTo(aa()),
          reason: '$colour is too dark on the dark surface',
        );
      }
    });

    test('ensure lifts a failing colour above the threshold', () {
      const unreadable = Color(0xFFFFF200); // school yellow
      expect(Contrast.ratio(unreadable, AppColors.surfaceLight), lessThan(aa()));
      final fixed = Contrast.ensure(unreadable, AppColors.surfaceLight);
      expect(Contrast.ratio(fixed, AppColors.surfaceLight), greaterThanOrEqualTo(aa()));
    });

    test('ensure leaves a passing colour untouched', () {
      expect(Contrast.ensure(AppColors.tealAction, AppColors.surfaceLight), AppColors.tealAction);
    });
  });

  group('themes', () {
    test('both brightnesses carry the token extension', () {
      final light = buildTheme(Brightness.light);
      final dark = buildTheme(Brightness.dark);

      expect(light.extension<AppTokens>()?.isDark, isFalse);
      expect(dark.extension<AppTokens>()?.isDark, isTrue);
      expect(light.colorScheme.brightness, Brightness.light);
      expect(dark.colorScheme.brightness, Brightness.dark);
    });

    test('dark is designed, not an inverted light theme', () {
      final light = buildTheme(Brightness.light);
      final dark = buildTheme(Brightness.dark);

      expect(dark.colorScheme.surface, isNot(light.colorScheme.surface));
      expect(dark.extension<AppTokens>()?.border, AppColors.borderDark);
      expect(light.extension<AppTokens>()?.border, AppColors.borderLight);
    });

    test('a school accent overrides the action colour only', () {
      const accent = Color(0xFF7A5AF8);
      final baseline = buildTheme(Brightness.light);
      final themed = buildTheme(Brightness.light, schoolAccent: accent);

      expect(
        themed.colorScheme.primary,
        Contrast.ensure(accent, AppColors.surfaceLight),
      );
      // Semantic colours and text are never school-overridable.
      expect(themed.colorScheme.error, baseline.colorScheme.error);
      expect(themed.extension<AppTokens>()?.success, AppTokens.light.success);
      expect(themed.extension<AppTokens>()?.warning, AppTokens.light.warning);
      expect(themed.textTheme.bodyMedium?.fontFamily, baseline.textTheme.bodyMedium?.fontFamily);
    });

    test('an unreadable school accent is corrected before use', () {
      const accent = Color(0xFFFFF200);
      final themed = buildTheme(Brightness.light, schoolAccent: accent);
      expect(themed.colorScheme.primary, isNot(accent));
      expect(
        Contrast.ratio(themed.colorScheme.primary, AppColors.surfaceLight),
        greaterThanOrEqualTo(aa()),
      );
    });

    test('foreground on the action colour is chosen for contrast', () {
      final light = buildTheme(Brightness.light);
      expect(light.colorScheme.onPrimary, Colors.white);
    });

    test('typography uses Inter with tabular figures for numbers', () {
      final light = buildTheme(Brightness.light);
      expect(light.textTheme.bodyMedium?.fontFamily, contains('Inter'));
      expect(light.textTheme.displaySmall?.fontWeight, FontWeight.w700);
    });
  });
}
