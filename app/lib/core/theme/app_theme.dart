import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import 'tokens.dart';
import 'typography.dart';

/// Builds the Trixx Education [ThemeData] for a brightness, optionally with a
/// school accent override.
///
/// [schoolAccent] must already be contrast-corrected against the surface (the
/// specification requires at least 4.5:1); [Contrast.ensure] is available for
/// callers that read a raw school colour from the API. Semantic colours
/// (success, warning, error, info), typography, and spacing are never
/// overridable by a school.
ThemeData buildTheme(Brightness brightness, {Color? schoolAccent}) {
  final isDark = brightness == Brightness.dark;
  final tokens = isDark ? AppTokens.dark : AppTokens.light;
  final surface = isDark ? AppColors.surfaceDark : AppColors.surfaceLight;
  final onSurface = isDark ? AppColors.textPrimaryDark : AppColors.textPrimaryLight;

  final rawAction = schoolAccent ?? (isDark ? AppColors.actionDark : AppColors.actionLight);
  final action = schoolAccent == null ? rawAction : Contrast.ensure(rawAction, surface, min: 4.5);

  final scheme = ColorScheme.fromSeed(
    seedColor: AppColors.tealAction,
    brightness: brightness,
  ).copyWith(
    primary: action,
    onPrimary: Contrast.luminance(action) > 0.5 ? const Color(0xFF00201C) : Colors.white,
    secondary: isDark ? AppColors.tealBrand : AppColors.tealBrand,
    onSecondary: Colors.white,
    tertiary: AppColors.amber,
    surface: surface,
    onSurface: onSurface,
    surfaceContainerLowest: tokens.canvas,
    surfaceContainerLow: surface,
    surfaceContainer: isDark ? AppColors.surfaceElevatedDark : const Color(0xFFF3F6FB),
    surfaceContainerHigh: tokens.surfaceElevated,
    onSurfaceVariant: tokens.textSecondary,
    outline: isDark ? const Color(0xFF4A5A70) : const Color(0xFF9AA7BA),
    outlineVariant: tokens.border,
    error: isDark ? AppColors.errorDark : AppColors.errorLight,
    onError: isDark ? const Color(0xFF3A0906) : Colors.white,
  );

  final textTheme = AppType.build(
    primary: onSurface,
    secondary: tokens.textSecondary,
  );

  final border = OutlineInputBorder(
    borderRadius: AppRadius.mdAll,
    borderSide: BorderSide(color: tokens.border),
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    fontFamily: AppType.family,
    fontFamilyFallback: AppType.fallback,
    textTheme: textTheme,
    scaffoldBackgroundColor: tokens.canvas,
    canvasColor: tokens.canvas,
    dividerColor: tokens.border,
    splashFactory: InkSparkle.splashFactory,
    visualDensity: VisualDensity.standard,
    extensions: <ThemeExtension<dynamic>>[tokens],
    appBarTheme: AppBarTheme(
      backgroundColor: surface,
      foregroundColor: onSurface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      toolbarHeight: AppLayout.topBarHeight,
      shape: Border(bottom: BorderSide(color: tokens.border)),
      titleTextStyle: textTheme.titleMedium,
    ),
    cardTheme: CardThemeData(
      color: surface,
      surfaceTintColor: Colors.transparent,
      elevation: isDark ? 0 : 1,
      shadowColor: isDark ? Colors.transparent : const Color(0x1A101828),
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: AppRadius.lgAll,
        side: BorderSide(color: tokens.border),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: isDark ? AppColors.surfaceElevatedDark : surface,
      border: border,
      enabledBorder: border,
      focusedBorder: OutlineInputBorder(
        borderRadius: AppRadius.mdAll,
        borderSide: BorderSide(color: scheme.primary, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: AppRadius.mdAll,
        borderSide: BorderSide(color: scheme.error),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: AppRadius.mdAll,
        borderSide: BorderSide(color: scheme.error, width: 2),
      ),
      contentPadding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.s12,
        vertical: AppSpacing.s12,
      ),
      labelStyle: textTheme.labelMedium?.copyWith(color: tokens.textSecondary),
      floatingLabelStyle: textTheme.labelMedium?.copyWith(color: scheme.primary),
      helperStyle: textTheme.bodySmall,
      errorStyle: textTheme.bodySmall?.copyWith(color: scheme.error),
      hintStyle: textTheme.bodyMedium?.copyWith(color: tokens.textSecondary),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(64, AppLayout.minTouchTarget),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s16, vertical: AppSpacing.s12),
        textStyle: textTheme.labelLarge,
        shape: const RoundedRectangleBorder(borderRadius: AppRadius.mdAll),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(64, AppLayout.minTouchTarget),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s16, vertical: AppSpacing.s12),
        textStyle: textTheme.labelLarge,
        foregroundColor: onSurface,
        side: BorderSide(color: tokens.border),
        shape: const RoundedRectangleBorder(borderRadius: AppRadius.mdAll),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        minimumSize: const Size(48, 40),
        textStyle: textTheme.labelLarge,
        foregroundColor: scheme.primary,
        shape: const RoundedRectangleBorder(borderRadius: AppRadius.smAll),
      ),
    ),
    iconButtonTheme: IconButtonThemeData(
      style: IconButton.styleFrom(
        minimumSize: const Size(AppLayout.minTouchTarget, AppLayout.minTouchTarget),
        foregroundColor: tokens.textSecondary,
      ),
    ),
    dividerTheme: DividerThemeData(color: tokens.border, thickness: 1, space: 1),
    chipTheme: ChipThemeData(
      backgroundColor: isDark ? AppColors.surfaceElevatedDark : const Color(0xFFF3F6FB),
      side: BorderSide(color: tokens.border),
      labelStyle: textTheme.labelMedium,
      secondaryLabelStyle: textTheme.labelMedium,
      shape: const RoundedRectangleBorder(borderRadius: AppRadius.smAll),
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8, vertical: 2),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: tokens.surfaceElevated,
      surfaceTintColor: Colors.transparent,
      elevation: 3,
      shape: const RoundedRectangleBorder(borderRadius: AppRadius.xlAll),
      titleTextStyle: textTheme.titleLarge,
      contentTextStyle: textTheme.bodyMedium,
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: tokens.surfaceElevated,
      surfaceTintColor: Colors.transparent,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.xl)),
      ),
      elevation: 3,
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: isDark ? AppColors.surfaceElevatedDark : AppColors.textPrimaryLight,
      contentTextStyle: textTheme.bodyMedium?.copyWith(
        color: isDark ? AppColors.textPrimaryDark : Colors.white,
      ),
      shape: const RoundedRectangleBorder(borderRadius: AppRadius.mdAll),
      insetPadding: const EdgeInsets.all(AppSpacing.s16),
    ),
    navigationBarTheme: NavigationBarThemeData(
      height: 68,
      backgroundColor: surface,
      surfaceTintColor: Colors.transparent,
      indicatorColor: action.withValues(alpha: 0.14),
      elevation: 0,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => textTheme.labelSmall!.copyWith(
          color: states.contains(WidgetState.selected) ? scheme.primary : tokens.textSecondary,
          fontWeight: states.contains(WidgetState.selected) ? FontWeight.w600 : FontWeight.w500,
        ),
      ),
    ),
    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: surface,
      indicatorColor: action.withValues(alpha: 0.14),
      selectedIconTheme: IconThemeData(color: scheme.primary, size: 24),
      unselectedIconTheme: IconThemeData(color: tokens.textSecondary, size: 24),
      selectedLabelTextStyle: textTheme.labelMedium!.copyWith(
        color: scheme.primary,
        fontWeight: FontWeight.w600,
      ),
      unselectedLabelTextStyle: textTheme.labelMedium!.copyWith(color: tokens.textSecondary),
      elevation: 0,
    ),
    listTileTheme: ListTileThemeData(
      iconColor: tokens.textSecondary,
      textColor: onSurface,
      selectedColor: scheme.primary,
      selectedTileColor: action.withValues(alpha: 0.10),
      shape: const RoundedRectangleBorder(borderRadius: AppRadius.mdAll),
      minVerticalPadding: AppSpacing.s8,
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: isDark ? AppColors.surfaceElevatedDark : AppColors.navy,
        borderRadius: AppRadius.smAll,
      ),
      textStyle: textTheme.bodySmall?.copyWith(color: Colors.white),
      waitDuration: const Duration(milliseconds: 500),
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: tokens.surfaceElevated,
      surfaceTintColor: Colors.transparent,
      elevation: 3,
      shape: const RoundedRectangleBorder(borderRadius: AppRadius.mdAll),
      textStyle: textTheme.bodyMedium,
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: action.withValues(alpha: 0.16),
      circularTrackColor: action.withValues(alpha: 0.16),
    ),
    tabBarTheme: TabBarThemeData(
      labelColor: scheme.primary,
      unselectedLabelColor: tokens.textSecondary,
      labelStyle: textTheme.labelLarge,
      unselectedLabelStyle: textTheme.labelLarge,
      indicatorSize: TabBarIndicatorSize.label,
      dividerColor: tokens.border,
    ),
    scrollbarTheme: ScrollbarThemeData(
      thickness: WidgetStateProperty.all(8),
      radius: const Radius.circular(4),
      thumbColor: WidgetStateProperty.all(tokens.textSecondary.withValues(alpha: 0.4)),
    ),
    expansionTileTheme: ExpansionTileThemeData(
      iconColor: tokens.textSecondary,
      collapsedIconColor: tokens.textSecondary,
      textColor: onSurface,
      collapsedTextColor: onSurface,
      tilePadding: const EdgeInsets.symmetric(horizontal: AppSpacing.s16),
      childrenPadding: const EdgeInsets.fromLTRB(AppSpacing.s16, 0, AppSpacing.s16, AppSpacing.s12),
    ),
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {
        // One transition everywhere: the Material 3 fade-through keeps the
        // motion language identical on web, desktop, and mobile.
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
        TargetPlatform.macOS: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.windows: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.linux: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.fuchsia: FadeForwardsPageTransitionsBuilder(),
      },
    ),
  );
}

/// The default light and dark themes, exposed for the component gallery and
/// golden tests.
abstract final class AppTheme {
  static ThemeData light() => buildTheme(Brightness.light);
  static ThemeData dark() => buildTheme(Brightness.dark);
}
