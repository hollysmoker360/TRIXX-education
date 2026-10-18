import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../theme/tokens.dart';
import '../theme/typography.dart';
import 'charts.dart';
import 'motion.dart';

/// Standard panel with the correct radius and border.
class SectionCard extends StatelessWidget {
  const SectionCard({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.action,
    this.padding = const EdgeInsets.all(AppSpacing.s16),
    this.trailing,
  });

  final String title;
  final String? subtitle;
  final Widget child;
  final Widget? action;
  final Widget? trailing;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: context.texts.titleMedium),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(subtitle!, style: context.texts.bodySmall),
                      ],
                    ],
                  ),
                ),
                if (action != null) action!,
                if (trailing != null) ...[const SizedBox(width: AppSpacing.s8), trailing!],
              ],
            ),
            const SizedBox(height: AppSpacing.s12),
            child,
          ],
        ),
      ),
    );
  }
}

/// Colour plus icon plus text status — colour is never the only signal.
class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.label, required this.kind, this.icon});

  final String label;
  final StatusKind kind;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final scheme = context.scheme;
    final color = context.tokens.status(kind, scheme);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: AppRadius.smAll,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8, vertical: 3),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[
              Icon(icon, size: 14, color: color),
              const SizedBox(width: AppSpacing.s4),
            ],
            Text(
              label,
              style: context.texts.labelSmall?.copyWith(
                color: color,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Legacy pill kept for existing call sites; prefer [StatusChip].
class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.label, this.color});

  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final base = color ?? context.scheme.primary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8, vertical: 3),
      decoration: BoxDecoration(
        color: base.withValues(alpha: 0.12),
        borderRadius: AppRadius.pill,
      ),
      child: Text(
        label,
        style: context.texts.labelSmall?.copyWith(color: base),
      ),
    );
  }
}

enum MetricTrend { up, down, flat }

/// KPI tile: value, label, comparison period, and trend. Clickable only when it
/// leads somewhere useful (specification section 6.2).
///
/// Three optional upgrades over the plain tile:
/// * [numericValue] counts the figure up on first paint ([AnimatedCount]).
/// * [spark] draws a small trend line so the number carries its own history.
/// * [accent] paints a gradient rail along the top edge to mark the tile.
class MetricCard extends StatelessWidget {
  const MetricCard({
    super.key,
    required this.label,
    required this.value,
    this.period,
    this.trend,
    this.hint,
    this.icon,
    this.onTap,
    this.status,
    this.numericValue,
    this.valueFormatter,
    this.spark,
    this.accent,
  });

  final String label;
  final String value;
  final String? period;
  final MetricTrend? trend;
  final String? hint;
  final IconData? icon;
  final VoidCallback? onTap;
  final StatusKind? status;

  /// When set, the figure animates from zero to this value.
  final double? numericValue;

  /// Formatting for [numericValue]; defaults to a whole number.
  final String Function(double value)? valueFormatter;

  /// Recent history drawn beneath the tile.
  final List<double>? spark;

  /// Gradient rail along the top of the tile.
  final Gradient? accent;

  @override
  Widget build(BuildContext context) {
    final scheme = context.scheme;
    final trendColor = switch (trend) {
      MetricTrend.up => context.tokens.success,
      MetricTrend.down => scheme.error,
      MetricTrend.flat => context.tokens.textSecondary,
      null => context.tokens.textSecondary,
    };

    final figure = numericValue == null
        ? Text(value, style: AppType.metric(scheme.onSurface))
        : AnimatedCount(
            value: numericValue!,
            formatter: valueFormatter,
            style: AppType.metric(scheme.onSurface),
          );

    final body = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (accent != null) Container(height: 3, decoration: BoxDecoration(gradient: accent)),
        Padding(
          padding: const EdgeInsets.all(AppSpacing.s16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  if (icon != null) ...[
                    Icon(icon, size: 18, color: context.tokens.textSecondary),
                    const SizedBox(width: AppSpacing.s8),
                  ],
                  Expanded(
                    child: Text(
                      label,
                      style: context.texts.labelMedium?.copyWith(color: context.tokens.textSecondary),
                    ),
                  ),
                  if (status != null)
                    Padding(
                      padding: const EdgeInsets.only(left: AppSpacing.s4),
                      child: Icon(
                        Icons.circle,
                        size: 10,
                        color: context.tokens.status(status!, scheme),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.s8),
              figure,
              if (period != null || trend != null) ...[
                const SizedBox(height: AppSpacing.s4),
                Row(
                  children: [
                    if (trend != null) ...[
                      Icon(
                        switch (trend!) {
                          MetricTrend.up => Icons.trending_up,
                          MetricTrend.down => Icons.trending_down,
                          MetricTrend.flat => Icons.trending_flat,
                        },
                        size: 16,
                        color: trendColor,
                      ),
                      const SizedBox(width: AppSpacing.s4),
                    ],
                    if (period != null)
                      Expanded(
                        child: Text(period!, style: context.texts.bodySmall),
                      ),
                  ],
                ),
              ],
              if (hint != null) ...[
                const SizedBox(height: AppSpacing.s4),
                Text(hint!, style: context.texts.bodySmall),
              ],
              if (spark != null && spark!.length > 1) ...[
                const SizedBox(height: AppSpacing.s12),
                SizedBox(
                  height: 32,
                  child: TrendSparkline(values: spark!, color: scheme.primary, fill: true),
                ),
              ],
            ],
          ),
        ),
      ],
    );

    final card = Card(child: body);
    if (onTap == null) return Semantics(label: '$label $value', child: card);
    return Semantics(
      button: true,
      label: '$label $value',
      child: InkWell(
        onTap: onTap,
        borderRadius: AppRadius.lgAll,
        child: card,
      ),
    );
  }
}

/// The signature Trixx page header: a navy→teal gradient panel with an optional
/// eyebrow, title, supporting copy, actions, and free-form content beneath.
///
/// Every page that leads a section should open with one of these instead of a
/// plain heading — it is the strongest signal that the product is Trixx.
class HeroHeader extends StatelessWidget {
  const HeroHeader({
    super.key,
    this.eyebrow,
    required this.title,
    this.subtitle,
    this.actions = const [],
    this.child,
    this.icon,
    this.gradient,
    this.padding = const EdgeInsets.all(AppSpacing.s24),
  });

  final String? eyebrow;
  final String title;
  final String? subtitle;
  final List<Widget> actions;
  final Widget? child;
  final IconData? icon;
  final Gradient? gradient;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final effectiveGradient = gradient ?? AppGradients.heroFor(isDark);
    final onHero = Colors.white;
    final dim = Colors.white.withValues(alpha: 0.82);

    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: effectiveGradient,
        borderRadius: AppRadius.xlAll,
        boxShadow: AppElevation.forDark(isDark, AppElevation.glow(AppColors.tealBrand)),
      ),
      child: Stack(
        children: [
          // Soft highlight sweep so the panel is not a flat rectangle.
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: AppGradients.heroGlow,
                borderRadius: AppRadius.xlAll,
              ),
            ),
          ),
          Padding(
            padding: padding,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (eyebrow != null) ...[
                  Row(
                    children: [
                      if (icon != null) ...[
                        Icon(icon, size: 15, color: dim),
                        const SizedBox(width: AppSpacing.s8),
                      ],
                      Text(
                        eyebrow!.toUpperCase(),
                        style: context.texts.labelSmall?.copyWith(
                          color: dim,
                          letterSpacing: 0.9,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.s8),
                ],
                Text(
                  title,
                  style: context.texts.headlineSmall?.copyWith(
                    color: onHero,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: AppSpacing.s4),
                  Text(subtitle!, style: context.texts.bodyMedium?.copyWith(color: dim)),
                ],
                if (actions.isNotEmpty) ...[
                  const SizedBox(height: AppSpacing.s16),
                  Wrap(spacing: AppSpacing.s8, runSpacing: AppSpacing.s8, children: actions),
                ],
                if (child != null) ...[
                  const SizedBox(height: AppSpacing.s16),
                  child!,
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// A light-on-dark action button for [HeroHeader].
///
/// The theme's buttons are tuned for the page surface, where a translucent
/// white-on-white button would be invisible, so the hero carries its own variant.
class HeroAction extends StatelessWidget {
  const HeroAction({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      onPressed: onPressed,
      icon: Icon(icon, size: 18),
      label: Text(label),
      style: TextButton.styleFrom(
        foregroundColor: Colors.white,
        backgroundColor: Colors.white.withValues(alpha: 0.14),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.s16,
          vertical: AppSpacing.s12,
        ),
        minimumSize: const Size(64, AppLayout.minTouchTarget),
        shape: const RoundedRectangleBorder(borderRadius: AppRadius.mdAll),
      ),
    );
  }
}

/// Frosted translucent panel used for floating chrome that sits over content —
/// the command palette, sticky sub-headers, and in-page overlays.
///
/// Blur is skipped when the platform asks for reduced motion or transparency so
/// low-end devices do not pay for it.
class GlassPanel extends StatelessWidget {
  const GlassPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpacing.s16),
    this.radius = AppRadius.lg,
    this.border = true,
    this.opacity,
  });

  final Widget child;
  final EdgeInsets padding;
  final double radius;
  final bool border;
  final double? opacity;

  @override
  Widget build(BuildContext context) {
    final isDark = context.isDark;
    final reduceTransparency = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    final base = Theme.of(context).colorScheme.surface;
    final alpha = opacity ?? AppGlass.alpha(isDark);

    final content = DecoratedBox(
      decoration: BoxDecoration(
        color: base.withValues(alpha: alpha),
        borderRadius: BorderRadius.circular(radius),
        border: border
            ? Border.all(color: context.tokens.border.withValues(alpha: AppGlass.borderAlpha))
            : null,
      ),
      child: Padding(padding: padding, child: child),
    );

    if (reduceTransparency) return content;
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: AppGlass.blur, sigmaY: AppGlass.blur),
        child: content,
      ),
    );
  }
}

/// Inline info / warning / success / error message.
class AppBanner extends StatelessWidget {
  const AppBanner({
    super.key,
    required this.message,
    this.kind = StatusKind.info,
    this.title,
    this.action,
    this.onDismiss,
  });

  final String message;
  final StatusKind kind;
  final String? title;
  final Widget? action;
  final VoidCallback? onDismiss;

  @override
  Widget build(BuildContext context) {
    final color = context.tokens.status(kind, context.scheme);
    final icon = switch (kind) {
      StatusKind.success => Icons.check_circle_outline,
      StatusKind.warning => Icons.warning_amber_outlined,
      StatusKind.error => Icons.error_outline,
      StatusKind.info => Icons.info_outline,
      StatusKind.pending => Icons.schedule,
      StatusKind.neutral => Icons.info_outline,
    };
    return Semantics(
      liveRegion: true,
      // The title carries the meaning; repeat it for screen readers.
      label: [title, message].whereType<String>().join('. '),
      child: Container(
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: AppRadius.mdAll,
          border: Border.all(color: color.withValues(alpha: 0.35)),
        ),
        padding: const EdgeInsets.all(AppSpacing.s12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 20, color: color),
            const SizedBox(width: AppSpacing.s12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (title != null) ...[
                    Text(title!, style: context.texts.titleSmall?.copyWith(color: color)),
                    const SizedBox(height: 2),
                  ],
                  Text(message, style: context.texts.bodyMedium),
                ],
              ),
            ),
            if (action != null) ...[const SizedBox(width: AppSpacing.s8), action!],
            if (onDismiss != null)
              IconButton(
                onPressed: onDismiss,
                icon: const Icon(Icons.close, size: 18),
                tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
              ),
          ],
        ),
      ),
    );
  }
}

/// How the user resolved a sync conflict.
enum ConflictChoice { keepLocal, useServer, merge }

/// Shows the local and server versions side by side with changed fields
/// highlighted, and requires an explicit choice (specification section 8.3).
Future<ConflictChoice?> showConflictResolver(
  BuildContext context, {
  required Map<String, dynamic> local,
  required Map<String, dynamic> server,
  String? entityLabel,
}) {
  final keys = <String>{...local.keys, ...server.keys}.toList()..sort();
  final changed = keys.where((k) => '${local[k]}' != '${server[k]}').toList();
  return showDialog<ConflictChoice>(
    context: context,
    builder: (context) {
      final s = S.of(context);
      return AlertDialog(
        title: Text(s.conflictTitle),
        content: SizedBox(
          width: 560,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  entityLabel == null ? s.conflictIntro : '${s.conflictIntro} ($entityLabel)',
                  style: context.texts.bodyMedium,
                ),
                const SizedBox(height: AppSpacing.s12),
                Text(s.changedFields, style: context.texts.labelMedium),
                const SizedBox(height: AppSpacing.s8),
                _ConflictTable(
                  changed: changed,
                  local: local,
                  server: server,
                  localLabel: s.localVersion,
                  serverLabel: s.serverVersion,
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(ConflictChoice.useServer),
            child: Text(s.useServer),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(ConflictChoice.keepLocal),
            child: Text(s.keepLocal),
          ),
        ],
      );
    },
  );
}

class _ConflictTable extends StatelessWidget {
  const _ConflictTable({
    required this.changed,
    required this.local,
    required this.server,
    required this.localLabel,
    required this.serverLabel,
  });

  final List<String> changed;
  final Map<String, dynamic> local;
  final Map<String, dynamic> server;
  final String localLabel;
  final String serverLabel;

  @override
  Widget build(BuildContext context) {
    if (changed.isEmpty) {
      return Text(S.of(context).noFieldChanges, style: context.texts.bodySmall);
    }
    return Table(
      border: TableBorder.all(color: context.tokens.border),
      columnWidths: const {0: FlexColumnWidth(1.2), 1: FlexColumnWidth(), 2: FlexColumnWidth()},
      children: [
        TableRow(
          decoration: BoxDecoration(color: context.tokens.canvas),
          children: [
            _cell(context, '', bold: true),
            _cell(context, localLabel, bold: true),
            _cell(context, serverLabel, bold: true),
          ],
        ),
        for (final key in changed)
          TableRow(
            children: [
              _cell(context, key, bold: true),
              _cell(context, '${local[key] ?? '—'}'),
              _cell(context, '${server[key] ?? '—'}'),
            ],
          ),
      ],
    );
  }

  Widget _cell(BuildContext context, String text, {bool bold = false}) => Padding(
        padding: const EdgeInsets.all(AppSpacing.s8),
        child: Text(
          text,
          style: context.texts.bodySmall?.copyWith(
            fontWeight: bold ? FontWeight.w600 : FontWeight.w400,
          ),
        ),
      );
}
