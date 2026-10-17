import 'package:flutter/material.dart';

import '../theme/tokens.dart';

/// Motion helpers. Every animation here is *informational* — it shows that
/// content arrived or that a number changed — and every one of them collapses
/// to a static frame when the platform asks for reduced motion
/// (`MediaQuery.disableAnimations`), which is what accessibility guidance and
/// low-bandwidth devices want.

/// Counts up to [value] on first build.
///
/// Used for KPI figures so a dashboard feels alive without decorative
/// entrances. Pass [formatter] for units, percentages, or fixed decimals.
class AnimatedCount extends StatelessWidget {
  const AnimatedCount({
    super.key,
    required this.value,
    this.style,
    this.formatter,
    this.duration = AppMotion.count,
  });

  final double value;
  final TextStyle? style;
  final String Function(double value)? formatter;
  final Duration duration;

  static String defaultFormat(double value) => value == value.roundToDouble()
      ? value.toStringAsFixed(0)
      : value.toStringAsFixed(1);

  @override
  Widget build(BuildContext context) {
    final format = formatter ?? defaultFormat;
    final reduceMotion = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    if (reduceMotion) return Text(format(value), style: style);
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: value),
      duration: duration,
      curve: AppMotion.emphasis,
      builder: (context, animated, _) => Text(format(animated), style: style),
    );
  }
}

/// Fades and lifts [child] into place once, optionally after [delay].
///
/// Wrap the sections of a page to give them a sense of arrival. Keep the delay
/// small and the offset tiny: this is polish, not a showreel.
class EnterFade extends StatefulWidget {
  const EnterFade({
    super.key,
    required this.child,
    this.delay = Duration.zero,
    this.offset = 12,
    this.duration = AppMotion.enter,
  });

  final Widget child;
  final Duration delay;

  /// Vertical travel in logical pixels.
  final double offset;
  final Duration duration;

  @override
  State<EnterFade> createState() => _EnterFadeState();
}

class _EnterFadeState extends State<EnterFade> {
  bool _shown = false;

  @override
  void initState() {
    super.initState();
    if (widget.delay == Duration.zero) {
      _shown = true;
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      Future<void>.delayed(widget.delay, () {
        if (mounted) setState(() => _shown = true);
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    if (reduceMotion) return widget.child;
    return AnimatedSlide(
      offset: _shown ? Offset.zero : Offset(0, widget.offset / 100),
      duration: widget.duration,
      curve: AppMotion.emphasis,
      child: AnimatedOpacity(
        opacity: _shown ? 1 : 0,
        duration: widget.duration,
        curve: AppMotion.standard,
        child: widget.child,
      ),
    );
  }
}

/// Lays children out vertically, staggering each [EnterFade].
///
/// The stagger is capped so a long list never looks like it is loading slowly.
class StaggeredColumn extends StatelessWidget {
  const StaggeredColumn({
    super.key,
    required this.children,
    this.perItem = AppMotion.stagger,
    this.maxDelay = const Duration(milliseconds: 240),
    this.crossAxisAlignment = CrossAxisAlignment.stretch,
    this.spacing = AppSpacing.s16,
  });

  final List<Widget> children;
  final Duration perItem;
  final Duration maxDelay;
  final CrossAxisAlignment crossAxisAlignment;
  final double spacing;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: crossAxisAlignment,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) SizedBox(height: spacing),
          EnterFade(
            delay: Duration(
              milliseconds: (perItem.inMilliseconds * i).clamp(0, maxDelay.inMilliseconds),
            ),
            child: children[i],
          ),
        ],
      ],
    );
  }
}
