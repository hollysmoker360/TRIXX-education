import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Trixx Education design tokens — the single source of visual truth.
///
/// Widgets must never hardcode a colour, size, radius, or duration; they read
/// from here (or from the theme) so that light and dark themes, school branding
/// overrides, and future re-skins stay consistent. See specification section 4.
abstract final class AppColors {
  // Brand (section 2.5).
  static const navy = Color(0xFF172B4D);
  static const tealAction = Color(0xFF087F73);
  static const tealBrand = Color(0xFF0F9F91);
  static const tealLight = Color(0xFF52D4C2);
  static const amber = Color(0xFFE6A23C);
  static const canvas = Color(0xFFF5F7FB);
  static const slate = Color(0xFF5B6B80);

  // Light theme roles (section 4.1).
  static const primaryLight = navy;
  static const actionLight = tealAction;
  static const backgroundLight = canvas;
  static const surfaceLight = Color(0xFFFFFFFF);
  static const surfaceElevatedLight = Color(0xFFFFFFFF);
  static const textPrimaryLight = Color(0xFF182230);
  static const textSecondaryLight = slate;
  static const borderLight = Color(0xFFDCE3EC);
  static const successLight = Color(0xFF18794E);
  static const warningLight = Color(0xFF946200);
  static const errorLight = Color(0xFFB42318);
  static const infoLight = Color(0xFF2459A9);

  // Dark theme roles (section 4.1) — designed separately, not inverted.
  static const primaryDark = Color(0xFFC4D5F5);
  static const actionDark = tealLight;
  static const backgroundDark = Color(0xFF101722);
  static const surfaceDark = Color(0xFF192433);
  static const surfaceElevatedDark = Color(0xFF222F40);
  static const textPrimaryDark = Color(0xFFF1F5F9);
  static const textSecondaryDark = Color(0xFFB0BDCC);
  static const borderDark = Color(0xFF354356);
  static const successDark = Color(0xFF65D6A0);
  static const warningDark = Color(0xFFF3C66A);
  static const errorDark = Color(0xFFFF8A80);
  static const infoDark = Color(0xFF8CB7FF);

  /// Categorical data-visualisation palette (section 4.2). Always pair colour
  /// with a label, pattern, or marker.
  static const seriesLight = <Color>[
    Color(0xFF0F9F91),
    Color(0xFF2459A9),
    Color(0xFFE6A23C),
    Color(0xFF7A5AF8),
    Color(0xFFD6456B),
    Color(0xFF64748B),
  ];

  static const seriesDark = <Color>[
    Color(0xFF52D4C2),
    Color(0xFF8CB7FF),
    Color(0xFFF3C66A),
    Color(0xFFB7A4FF),
    Color(0xFFFF9CB5),
    Color(0xFFB0BDCC),
  ];
}

/// Spacing scale (4 px base, section 4.4).
abstract final class AppSpacing {
  static const double s4 = 4;
  static const double s8 = 8;
  static const double s12 = 12;
  static const double s16 = 16;
  static const double s24 = 24;
  static const double s32 = 32;
  static const double s48 = 48;

  // Aliases matching the sample in the specification.
  static const double xs = s4;
  static const double sm = s8;
  static const double md = s16;
  static const double lg = s24;
  static const double xl = s32;

  /// Page gutters by window class.
  static const double gutterCompact = s16;
  static const double gutterMedium = s24;
  static const double gutterExpanded = s32;
}

/// Corner radii (section 4.4). Use the radius that matches the element.
abstract final class AppRadius {
  static const double sm = 6; // chips
  static const double md = 10; // inputs, buttons
  static const double lg = 14; // cards
  static const double xl = 20; // dialogs, sheets

  static const BorderRadius smAll = BorderRadius.all(Radius.circular(sm));
  static const BorderRadius mdAll = BorderRadius.all(Radius.circular(md));
  static const BorderRadius lgAll = BorderRadius.all(Radius.circular(lg));
  static const BorderRadius xlAll = BorderRadius.all(Radius.circular(xl));
  static const BorderRadius pill = BorderRadius.all(Radius.circular(999));
}

/// Motion (section 4.4). Motion answers an action; no decorative entrances.
abstract final class AppMotion {
  static const Duration stateChange = Duration(milliseconds: 150);
  static const Duration expandCollapse = Duration(milliseconds: 200);
  static const Duration pageTransition = Duration(milliseconds: 250);

  /// Content entering a page (hero panels, staggered lists).
  static const Duration enter = Duration(milliseconds: 320);
  static const Duration enterSlow = Duration(milliseconds: 460);

  /// Numbers that count up to their final value.
  static const Duration count = Duration(milliseconds: 700);

  /// Per-item offset used to stagger a list entrance.
  static const Duration stagger = Duration(milliseconds: 45);

  /// A calm, decisive curve — no overshoot. Decorative bounce is banned.
  static const Curve emphasis = Curves.easeOutCubic;
  static const Curve standard = Curves.easeInOut;
}

/// Brand gradients. Gradients are reserved for hero surfaces and accents so
/// they never compete with data; every gradient here has a flat fallback colour
/// under high-contrast or low-bandwidth preferences.
abstract final class AppGradients {
  /// Deep navy → teal. The signature Trixx hero.
  static const hero = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [AppColors.navy, Color(0xFF124F5C), AppColors.tealBrand],
    stops: [0, 0.55, 1],
  );

  /// Teal → light teal, for accent rails and progress.
  static const teal = LinearGradient(
    begin: Alignment.centerLeft,
    end: Alignment.centerRight,
    colors: [AppColors.tealAction, AppColors.tealBrand],
  );

  /// Amber → teal, used sparingly for "needs attention" accents.
  static const attention = LinearGradient(
    begin: Alignment.centerLeft,
    end: Alignment.centerRight,
    colors: [AppColors.amber, AppColors.tealBrand],
  );

  /// Soft radial wash placed behind the hero copy.
  static const heroGlow = RadialGradient(
    center: Alignment(-0.75, -0.9),
    radius: 1.35,
    colors: [Color(0x33FFFFFF), Color(0x00FFFFFF)],
  );

  /// Dark-theme hero: the navy hero reads too flat on a dark canvas.
  static const heroDark = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF1B2A44), Color(0xFF123F49), Color(0xFF0F6F69)],
    stops: [0, 0.55, 1],
  );

  static LinearGradient heroFor(bool isDark) => isDark ? heroDark : hero;
}

/// Glass (translucency + blur) used by the top bar, command palette, and
/// overlay panels. [alpha] values are tuned per theme so text contrast holds.
abstract final class AppGlass {
  static const double blur = 20;
  static const double alphaLight = 0.78;
  static const double alphaDark = 0.66;
  static const double borderAlpha = 0.55;

  static double alpha(bool isDark) => isDark ? alphaDark : alphaLight;
}

/// Shadows. Elevation is expressed as one named recipe per level rather than an
/// ad-hoc [BoxShadow] per widget.
abstract final class AppElevation {
  /// Resting card.
  static const List<BoxShadow> card = [
    BoxShadow(color: Color(0x0F101828), blurRadius: 2, offset: Offset(0, 1)),
    BoxShadow(color: Color(0x14101828), blurRadius: 12, offset: Offset(0, 4)),
  ];

  /// Raised surface (popovers, drag handles, sticky headers).
  static const List<BoxShadow> raised = [
    BoxShadow(color: Color(0x17101828), blurRadius: 8, offset: Offset(0, 2)),
    BoxShadow(color: Color(0x1F101828), blurRadius: 28, offset: Offset(0, 12)),
  ];

  /// Overlay (dialog, sheet, command palette).
  static const List<BoxShadow> overlay = [
    BoxShadow(color: Color(0x24101828), blurRadius: 16, offset: Offset(0, 6)),
    BoxShadow(color: Color(0x33101828), blurRadius: 48, offset: Offset(0, 24)),
  ];

  /// Coloured halo under the hero panel.
  static List<BoxShadow> glow(Color colour) => [
        BoxShadow(color: colour.withValues(alpha: 0.28), blurRadius: 32, offset: const Offset(0, 14)),
      ];

  static List<BoxShadow> forDark(bool isDark, List<BoxShadow> shadows) =>
      isDark ? const [] : shadows;
}

/// Interaction and layout constants.
abstract final class AppLayout {
  static const double maxContentWidth = 1440;
  static const double sidebarWidth = 248;
  static const double topBarHeight = 56;
  static const double minTouchTarget = 44;
}

/// Status kinds used by chips, banners, and metrics.
enum StatusKind { success, warning, error, info, neutral, pending }

/// Accent-neutral tokens that [ColorScheme] does not carry. Extended onto
/// [ThemeData] so widgets can read them with `context.tokens`.
@immutable
class AppTokens extends ThemeExtension<AppTokens> {
  const AppTokens({
    required this.success,
    required this.warning,
    required this.info,
    required this.border,
    required this.canvas,
    required this.surfaceElevated,
    required this.textSecondary,
    required this.series,
    required this.isDark,
  });

  final Color success;
  final Color warning;
  final Color info;
  final Color border;
  final Color canvas;
  final Color surfaceElevated;
  final Color textSecondary;
  final List<Color> series;
  final bool isDark;

  static const light = AppTokens(
    success: AppColors.successLight,
    warning: AppColors.warningLight,
    info: AppColors.infoLight,
    border: AppColors.borderLight,
    canvas: AppColors.backgroundLight,
    surfaceElevated: AppColors.surfaceElevatedLight,
    textSecondary: AppColors.textSecondaryLight,
    series: AppColors.seriesLight,
    isDark: false,
  );

  static const dark = AppTokens(
    success: AppColors.successDark,
    warning: AppColors.warningDark,
    info: AppColors.infoDark,
    border: AppColors.borderDark,
    canvas: AppColors.backgroundDark,
    surfaceElevated: AppColors.surfaceElevatedDark,
    textSecondary: AppColors.textSecondaryDark,
    series: AppColors.seriesDark,
    isDark: true,
  );

  Color status(StatusKind kind, ColorScheme scheme) => switch (kind) {
        StatusKind.success => success,
        StatusKind.warning => warning,
        StatusKind.error => scheme.error,
        StatusKind.info => info,
        StatusKind.neutral => scheme.onSurfaceVariant,
        StatusKind.pending => warning,
      };

  @override
  AppTokens copyWith({
    Color? success,
    Color? warning,
    Color? info,
    Color? border,
    Color? canvas,
    Color? surfaceElevated,
    Color? textSecondary,
    List<Color>? series,
    bool? isDark,
  }) =>
      AppTokens(
        success: success ?? this.success,
        warning: warning ?? this.warning,
        info: info ?? this.info,
        border: border ?? this.border,
        canvas: canvas ?? this.canvas,
        surfaceElevated: surfaceElevated ?? this.surfaceElevated,
        textSecondary: textSecondary ?? this.textSecondary,
        series: series ?? this.series,
        isDark: isDark ?? this.isDark,
      );

  @override
  AppTokens lerp(ThemeExtension<AppTokens>? other, double t) {
    if (other is! AppTokens) return this;
    return AppTokens(
      success: Color.lerp(success, other.success, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
      info: Color.lerp(info, other.info, t)!,
      border: Color.lerp(border, other.border, t)!,
      canvas: Color.lerp(canvas, other.canvas, t)!,
      surfaceElevated: Color.lerp(surfaceElevated, other.surfaceElevated, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      series: [
        for (var i = 0; i < series.length; i++)
          Color.lerp(series[i], other.series[i % other.series.length], t)!,
      ],
      isDark: t < 0.5 ? isDark : other.isDark,
    );
  }
}

extension AppTokensX on BuildContext {
  AppTokens get tokens => Theme.of(this).extension<AppTokens>() ?? AppTokens.light;
  ColorScheme get scheme => Theme.of(this).colorScheme;
  TextTheme get texts => Theme.of(this).textTheme;
  bool get isDark => Theme.of(this).brightness == Brightness.dark;
}

/// Maps a [StatusKind] to a representative color for chips, badges, and dots.
extension ColorSchemeStatus on ColorScheme {
  Color status(StatusKind kind) {
    switch (kind) {
      case StatusKind.success:
        return Colors.green;
      case StatusKind.warning:
        return Colors.amber;
      case StatusKind.error:
        return Colors.red;
      case StatusKind.info:
        return Colors.blue;
      case StatusKind.neutral:
      case StatusKind.pending:
        return Colors.grey;
    }
  }
}

/// Relative luminance / contrast helpers (WCAG 2.2). Used to keep school accent
/// colours and status foregrounds readable (sections 4.5 and 9).
abstract final class Contrast {
  static double _channel(double c) {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : math.pow((c + 0.055) / 1.055, 2.4).toDouble();
  }

  static double luminance(Color c) =>
      0.2126 * _channel(c.r * 255) + 0.7152 * _channel(c.g * 255) + 0.0722 * _channel(c.b * 255);

  static double ratio(Color a, Color b) {
    final la = luminance(a) + 0.05;
    final lb = luminance(b) + 0.05;
    return la > lb ? la / lb : lb / la;
  }

  /// Darkens or lightens [color] until it reaches [min] against [background].
  /// School accent colours pass through here before entering the theme.
  static Color ensure(Color color, Color background, {double min = 4.5}) {
    if (ratio(color, background) >= min) return color;
    final towards = luminance(background) > 0.5 ? Colors.black : Colors.white;
    var best = color;
    for (var t = 0.02; t <= 1.0; t += 0.02) {
      final candidate = Color.lerp(color, towards, t)!;
      if (ratio(candidate, background) >= min) {
        best = candidate;
        break;
      }
    }
    return best;
  }
}
