import 'package:flutter/material.dart';

/// Typography scale (specification section 4.3).
///
/// Inter is bundled as an asset (offline schools must work without a runtime
/// download) with the platform sans-serif as fallback. Numbers in tables,
/// finance, and marks use tabular figures so columns align.
abstract final class AppType {
  static const family = 'Inter';
  static const fallback = <String>['Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'];

  /// Tabular figures for any numeric text so columns and totals line up.
  static const tabular = <FontFeature>[FontFeature.tabularFigures()];

  static TextTheme build({required Color primary, required Color secondary}) {
    TextStyle s(double size, FontWeight weight, {Color? color, double? height, double? spacing}) =>
        TextStyle(
          fontFamily: family,
          fontFamilyFallback: fallback,
          fontSize: size,
          fontWeight: weight,
          height: height,
          letterSpacing: spacing,
          color: color,
        );

    return TextTheme(
      // Display — sign-in and marketing screens.
      displaySmall: s(32, FontWeight.w700, color: primary, height: 1.2, spacing: -0.5),
      // Page title — one per page.
      headlineMedium: s(26, FontWeight.w700, color: primary, height: 1.25, spacing: -0.25),
      headlineSmall: s(22, FontWeight.w700, color: primary, height: 1.3),
      // Section title — cards and sections.
      titleLarge: s(20, FontWeight.w600, color: primary, height: 1.3),
      // Subheading — panels and form groups.
      titleMedium: s(16, FontWeight.w600, color: primary, height: 1.35),
      titleSmall: s(14, FontWeight.w600, color: primary, height: 1.4),
      // Body — default.
      bodyLarge: s(16, FontWeight.w400, color: primary, height: 1.45),
      bodyMedium: s(14, FontWeight.w400, color: primary, height: 1.45),
      bodySmall: s(12, FontWeight.w400, color: secondary, height: 1.4),
      // Label — fields and table headers.
      labelLarge: s(14, FontWeight.w600, color: primary, height: 1.4),
      labelMedium: s(13, FontWeight.w500, color: primary, height: 1.35),
      labelSmall: s(11, FontWeight.w500, color: secondary, height: 1.3, spacing: 0.2),
    );
  }

  /// KPI value style — 28 / 700, tabular (specification section 4.4).
  static TextStyle metric(Color color) => TextStyle(
        fontFamily: family,
        fontFamilyFallback: fallback,
        fontSize: 28,
        fontWeight: FontWeight.w700,
        height: 1.15,
        letterSpacing: -0.5,
        color: color,
        fontFeatures: tabular,
      );

  /// Applies tabular figures to an arbitrary numeric [TextStyle].
  static TextStyle numeric(TextStyle? base) =>
      (base ?? const TextStyle()).copyWith(fontFeatures: tabular);
}
