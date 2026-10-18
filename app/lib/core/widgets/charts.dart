import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../theme/tokens.dart';
import '../theme/typography.dart';

/// Wraps any chart with a text summary for screen readers and a data-table
/// alternative (specification sections 7.1 and 9).
class AccessibleChart extends StatefulWidget {
  const AccessibleChart({
    super.key,
    required this.summary,
    required this.chart,
    required this.rows,
    this.title,
    this.height = 220,
  });

  /// One-sentence description read by assistive technology.
  final String summary;
  final Widget chart;

  /// Tabular equivalent of the data ([label, value]).
  final List<MapEntry<String, String>> rows;
  final String? title;
  final double height;

  @override
  State<AccessibleChart> createState() => _AccessibleChartState();
}

class _AccessibleChartState extends State<AccessibleChart> {
  bool _asTable = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            if (widget.title != null)
              Expanded(child: Text(widget.title!, style: context.texts.titleSmall)),
            TextButton.icon(
              onPressed: () => setState(() => _asTable = !_asTable),
              icon: Icon(_asTable ? Icons.bar_chart : Icons.table_chart_outlined, size: 18),
              label: Text(_asTable ? S.of(context).showChart : S.of(context).showTable),
            ),
          ],
        ),
        if (_asTable)
          _DataTableAlt(rows: widget.rows)
        else
          Semantics(
            label: widget.summary,
            child: SizedBox(height: widget.height, child: widget.chart),
          ),
      ],
    );
  }
}

class _DataTableAlt extends StatelessWidget {
  const _DataTableAlt({required this.rows});

  final List<MapEntry<String, String>> rows;

  @override
  Widget build(BuildContext context) {
    return Table(
      border: TableBorder(horizontalInside: BorderSide(color: context.tokens.border)),
      columnWidths: const {0: FlexColumnWidth(2), 1: FlexColumnWidth()},
      children: [
        for (final entry in rows)
          TableRow(
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.s8),
                child: Text(entry.key, style: context.texts.bodyMedium),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(vertical: AppSpacing.s8),
                child: Text(
                  entry.value,
                  textAlign: TextAlign.end,
                  style: AppType.numeric(context.texts.bodyMedium),
                ),
              ),
            ],
          ),
      ],
    );
  }
}

/// Categorical bar chart. Colour is paired with a text label, and the palette
/// follows the documented series order.
///
/// Bars grow into place once on first paint; under reduced motion they are drawn
/// at full height immediately.
class SimpleBarChart extends StatefulWidget {
  const SimpleBarChart({super.key, required this.series, this.unit});

  /// Series index → name → value.
  final List<MapEntry<String, double>> series;
  final String? unit;

  @override
  State<SimpleBarChart> createState() => _SimpleBarChartState();
}

class _SimpleBarChartState extends State<SimpleBarChart> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: AppMotion.enterSlow,
  );

  @override
  void initState() {
    super.initState();
    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(SimpleBarChart oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.series != widget.series) _controller.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    final series = widget.series;
    final maxValue = series.fold<double>(0, (m, e) => math.max(m, e.value));
    final palette = context.tokens.series;
    final reduceMotion = MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    final growth = reduceMotion
        ? const AlwaysStoppedAnimation<double>(1)
        : CurvedAnimation(parent: _controller, curve: AppMotion.emphasis);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s8, vertical: AppSpacing.s8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var i = 0; i < series.length; i++)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s4),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Text(
                      widget.unit == null
                          ? _fmt(series[i].value)
                          : '${_fmt(series[i].value)}${widget.unit}',
                      style: AppType.numeric(context.texts.labelSmall),
                    ),
                    const SizedBox(height: AppSpacing.s4),
                    Flexible(
                      child: AnimatedBuilder(
                        animation: growth,
                        builder: (context, _) {
                          final target =
                              maxValue == 0 ? 0.0 : (series[i].value / maxValue).clamp(0.02, 1).toDouble();
                          return FractionallySizedBox(
                            heightFactor: target * growth.value,
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  begin: Alignment.bottomCenter,
                                  end: Alignment.topCenter,
                                  colors: [
                                    palette[i % palette.length],
                                    palette[i % palette.length].withValues(alpha: 0.62),
                                  ],
                                ),
                                borderRadius: const BorderRadius.vertical(
                                  top: Radius.circular(AppRadius.sm),
                                ),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: AppSpacing.s8),
                    Text(
                      series[i].key,
                      style: context.texts.labelSmall,
                      textAlign: TextAlign.center,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  static String _fmt(double value) =>
      value == value.roundToDouble() ? value.toStringAsFixed(0) : value.toStringAsFixed(1);
}

/// Legend for a chart, always pairing colour with a label.
class ChartLegend extends StatelessWidget {
  const ChartLegend({super.key, required this.entries});

  final List<String> entries;

  @override
  Widget build(BuildContext context) {
    final palette = context.tokens.series;
    return Wrap(
      spacing: AppSpacing.s16,
      runSpacing: AppSpacing.s8,
      children: [
        for (var i = 0; i < entries.length; i++)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 12,
                height: 12,
                decoration: BoxDecoration(
                  color: palette[i % palette.length],
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
              const SizedBox(width: AppSpacing.s8),
              Text(entries[i], style: context.texts.bodySmall),
            ],
          ),
      ],
    );
  }
}

/// Minimal polyline trend with tabular value labels.
///
/// With [fill] the area under the line carries a soft gradient, which makes a
/// bare sparkline readable as a volume rather than only a shape.
class TrendSparkline extends StatelessWidget {
  const TrendSparkline({
    super.key,
    required this.values,
    this.color,
    this.fill = false,
    this.showGrid = true,
  });

  final List<double> values;
  final Color? color;
  final bool fill;
  final bool showGrid;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _SparklinePainter(
        values: values,
        color: color ?? context.scheme.primary,
        grid: context.tokens.border,
        fill: fill,
        showGrid: showGrid,
      ),
      size: Size.infinite,
    );
  }
}

class _SparklinePainter extends CustomPainter {
  _SparklinePainter({
    required this.values,
    required this.color,
    required this.grid,
    this.fill = false,
    this.showGrid = true,
  });

  final List<double> values;
  final Color color;
  final Color grid;
  final bool fill;
  final bool showGrid;

  @override
  void paint(Canvas canvas, Size size) {
    if (values.length < 2) return;
    if (showGrid) {
      final paint = Paint()
        ..color = grid
        ..strokeWidth = 1;
      // Light horizontal grid so the trend is readable without a full axis.
      for (var i = 0; i <= 2; i++) {
        final y = size.height * i / 2;
        canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
      }
    }

    final minValue = values.reduce(math.min);
    final maxValue = values.reduce(math.max);
    final span = (maxValue - minValue).abs() < 0.0001 ? 1.0 : maxValue - minValue;
    final path = Path();
    for (var i = 0; i < values.length; i++) {
      final x = size.width * i / (values.length - 1);
      final y = size.height - ((values[i] - minValue) / span) * size.height;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }

    if (fill) {
      final area = Path.from(path)
        ..lineTo(size.width, size.height)
        ..lineTo(0, size.height)
        ..close();
      canvas.drawPath(
        area,
        Paint()
          ..shader = LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [color.withValues(alpha: 0.30), color.withValues(alpha: 0.02)],
          ).createShader(Offset.zero & size),
      );
    }

    canvas.drawPath(
      path,
      Paint()
        ..color = color
        ..strokeWidth = 2
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_SparklinePainter oldDelegate) =>
      oldDelegate.values != values ||
      oldDelegate.color != color ||
      oldDelegate.fill != fill ||
      oldDelegate.showGrid != showGrid;
}
