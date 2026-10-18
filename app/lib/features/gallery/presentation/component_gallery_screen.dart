import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/settings/preferences.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/theme/typography.dart';
import '../../../core/widgets/common.dart';

/// Component gallery (specification section 13.1, delivery step 4): every shared
/// component in light and dark themes, so regressions are caught by eye and by
/// golden tests. Rendered standalone so it can be screenshotted in CI.
class ComponentGalleryScreen extends ConsumerStatefulWidget {
  const ComponentGalleryScreen({super.key});

  @override
  ConsumerState<ComponentGalleryScreen> createState() => _ComponentGalleryScreenState();
}

class _ComponentGalleryScreenState extends ConsumerState<ComponentGalleryScreen> {
  Brightness _preview = Brightness.light;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    return ScreenScaffold(
      title: s.componentGallery,
      description: s.galleryHint,
      actions: [
        IconButton(
          tooltip: s.darkTheme,
          icon: Icon(_preview == Brightness.dark ? Icons.light_mode : Icons.dark_mode),
          onPressed: () => setState(
            () => _preview = _preview == Brightness.dark ? Brightness.light : Brightness.dark,
          ),
        ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Previewing ${_preview == Brightness.dark ? 'dark' : 'light'} theme '
            '(${ref.watch(themeModeProvider).name} preference)',
            style: context.texts.bodySmall,
          ),
          const SizedBox(height: AppSpacing.s12),
          Card(
            clipBehavior: Clip.antiAlias,
            child: Theme(
              data: buildTheme(_preview),
              child: Builder(
                builder: (context) => Padding(
                  padding: const EdgeInsets.all(AppSpacing.s16),
                  child: _GalleryBody(themeMode: _preview),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _GalleryBody extends StatelessWidget {
  const _GalleryBody({required this.themeMode});

  final Brightness themeMode;

  @override
  Widget build(BuildContext context) {
    final sampleSeries = <MapEntry<String, double>>[
      MapEntry('Grade 7', 84),
      MapEntry('Grade 8', 71),
      MapEntry('Grade 9', 63),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Section(
          title: 'Typography',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Display 32 / 700', style: context.texts.displaySmall),
              Text('Page title 26 / 700', style: context.texts.headlineMedium),
              Text('Section 20 / 600', style: context.texts.titleLarge),
              Text('Subheading 16 / 600', style: context.texts.titleMedium),
              Text('Body 14 / 400', style: context.texts.bodyMedium),
              Text('Caption 12 / 400', style: context.texts.bodySmall),
              const SizedBox(height: AppSpacing.s8),
              Text('Metric 28 / 700 tabular: 1,284.50', style: AppType.metric(context.scheme.onSurface)),
            ],
          ),
        ),
        _Section(
          title: 'Colours',
          child: Wrap(
            spacing: AppSpacing.s8,
            runSpacing: AppSpacing.s8,
            children: [
              _Swatch(name: 'Primary', color: context.scheme.primary),
              _Swatch(name: 'Action', color: context.scheme.primary),
              _Swatch(name: 'Success', color: context.tokens.success),
              _Swatch(name: 'Warning', color: context.tokens.warning),
              _Swatch(name: 'Error', color: context.scheme.error),
              _Swatch(name: 'Info', color: context.tokens.info),
              _Swatch(name: 'Border', color: context.tokens.border),
              _Swatch(name: 'Canvas', color: context.tokens.canvas),
            ],
          ),
        ),
        _Section(
          title: 'Gradients, hero, and glass',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Wrap(
                spacing: AppSpacing.s8,
                runSpacing: AppSpacing.s8,
                children: const [
                  _GradientSwatch(name: 'Hero', gradient: AppGradients.hero),
                  _GradientSwatch(name: 'Teal', gradient: AppGradients.teal),
                  _GradientSwatch(name: 'Attention', gradient: AppGradients.attention),
                ],
              ),
              const SizedBox(height: AppSpacing.s16),
              HeroHeader(
                eyebrow: 'Today',
                icon: Icons.today_outlined,
                title: 'Good morning, Grace',
                subtitle: 'Here is what needs you today.',
                actions: [
                  HeroAction(
                    icon: Icons.schedule_outlined,
                    label: 'Timetable',
                    onPressed: () {},
                  ),
                  HeroAction(
                    icon: Icons.fact_check_outlined,
                    label: 'Attendance',
                    onPressed: () {},
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.s12),
              const HeroHeader(
                eyebrow: 'Attention',
                icon: Icons.warning_amber_outlined,
                title: 'Amber → teal variant',
                subtitle: 'Reserved for "needs attention" panels.',
                gradient: AppGradients.attention,
              ),
              const SizedBox(height: AppSpacing.s12),
              // The blur only reads when there is something behind it.
              SizedBox(
                height: 104,
                child: Stack(
                  children: [
                    Container(
                      decoration: const BoxDecoration(
                        gradient: AppGradients.teal,
                        borderRadius: AppRadius.lgAll,
                      ),
                    ),
                    const Positioned.fill(
                      child: Padding(
                        padding: EdgeInsets.all(AppSpacing.s8),
                        child: GlassPanel(
                          child: Center(child: Text('GlassPanel over a gradient')),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        _Section(
          title: 'Status chips (colour + icon + text)',
          child: Wrap(
            spacing: AppSpacing.s8,
            runSpacing: AppSpacing.s8,
            children: const [
              StatusChip(label: 'Active', kind: StatusKind.success, icon: Icons.check_circle_outline),
              StatusChip(label: 'Pending', kind: StatusKind.pending, icon: Icons.schedule),
              StatusChip(label: 'Needs review', kind: StatusKind.warning, icon: Icons.warning_amber_outlined),
              StatusChip(label: 'Failed', kind: StatusKind.error, icon: Icons.error_outline),
              StatusChip(label: 'Info', kind: StatusKind.info, icon: Icons.info_outline),
              StatusChip(label: 'Draft', kind: StatusKind.neutral, icon: Icons.edit_note),
            ],
          ),
        ),
        _Section(
          title: 'Buttons',
          child: Wrap(
            spacing: AppSpacing.s8,
            runSpacing: AppSpacing.s8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              AppButton(label: 'Primary', onPressed: () {}),
              AppButton(label: 'Secondary', variant: AppButtonVariant.secondary, onPressed: () {}),
              AppButton(label: 'Outline', variant: AppButtonVariant.outline, onPressed: () {}),
              AppButton(label: 'Text', variant: AppButtonVariant.text, onPressed: () {}),
              AppButton(label: 'Destructive', variant: AppButtonVariant.destructive, onPressed: () {}),
              AppButton(label: 'Shown while busy', busy: true),
              AppButton(label: 'Disabled'),
              AppButton.icon(label: 'More', icon: Icons.more_horiz, tooltip: 'More', onPressed: () {}),
            ],
          ),
        ),
        _Section(
          title: 'Metrics (animated, with accent rail and sparkline)',
          child: LayoutBuilder(
            builder: (context, constraints) => Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'Enrolled students',
                    value: '1,284',
                    numericValue: 1284,
                    valueFormatter: (value) => value.round().toString(),
                    period: '+3.2% vs last term',
                    trend: MetricTrend.up,
                    icon: Icons.groups_outlined,
                    status: StatusKind.success,
                    accent: AppGradients.teal,
                  ),
                ),
                const SizedBox(width: AppSpacing.s12),
                Expanded(
                  child: MetricCard(
                    label: 'Attendance today',
                    value: '94%',
                    numericValue: 94,
                    valueFormatter: (value) => '${value.round()}%',
                    period: '-1.1% vs yesterday',
                    trend: MetricTrend.down,
                    icon: Icons.fact_check_outlined,
                    // Sample series: in the product a sparkline is only ever fed
                    // real history, never invented numbers.
                    spark: const [88, 90, 89, 92, 91, 93, 94],
                    accent: AppGradients.attention,
                  ),
                ),
              ],
            ),
          ),
        ),
        _Section(
          title: 'Motion (staggered entrance, honours reduce-motion)',
          child: StaggeredColumn(
            spacing: AppSpacing.s8,
            children: [
              for (final label in const ['First card enters', 'Then this one', 'And finally this'])
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.bolt_outlined),
                    title: Text(label),
                    subtitle: const Text('EnterFade with a per-item delay'),
                  ),
                ),
            ],
          ),
        ),
        _Section(
          title: 'Inputs',
          child: Column(
            children: [
              AppTextField(label: 'Full name', helper: 'As it appears on the admission form', required: true),
              const SizedBox(height: AppSpacing.s12),
              AppSelectField<String>(
                label: 'Class',
                value: 'Grade 8',
                items: const ['Grade 7', 'Grade 8', 'Grade 9'],
                itemLabel: (value) => value,
                onChanged: (_) {},
              ),
              const SizedBox(height: AppSpacing.s12),
              DateRangeField(
                label: 'Term dates',
                range: DateTimeRange(
                  start: DateTime(2026, 1, 5),
                  end: DateTime(2026, 4, 3),
                ),
                onChanged: (_) {},
              ),
              const SizedBox(height: AppSpacing.s12),
              FileUploadField(label: 'Attachment'),
            ],
          ),
        ),
        _Section(
          title: 'Banners and states',
          child: Column(
            children: [
              const AppBanner(kind: StatusKind.info, title: 'Heads up', message: 'Timetable publishes on Friday.'),
              const SizedBox(height: AppSpacing.s8),
              const AppBanner(kind: StatusKind.warning, message: 'You are offline. Changes are saved on this device.'),
              const SizedBox(height: AppSpacing.s8),
              const AppBanner(kind: StatusKind.success, message: 'Attendance saved.'),
              const SizedBox(height: AppSpacing.s8),
              const AppBanner(kind: StatusKind.error, message: 'Sync failed. Your work is safe.'),
            ],
          ),
        ),
        _Section(
          title: 'Charts (with table alternative)',
          child: Column(
            children: [
              AccessibleChart(
                title: 'Average score by grade',
                summary: 'Bar chart: Grade 7 average 84, Grade 8 average 71, Grade 9 average 63.',
                rows: const [MapEntry('Grade 7', '84'), MapEntry('Grade 8', '71'), MapEntry('Grade 9', '63')],
                chart: SimpleBarChart(series: sampleSeries, unit: '%'),
              ),
              const SizedBox(height: AppSpacing.s16),
              const ChartLegend(entries: ['Grade 7', 'Grade 8', 'Grade 9']),
              const SizedBox(height: AppSpacing.s16),
              SizedBox(height: 80, child: TrendSparkline(values: const [62, 66, 64, 70, 74, 71, 78, 82])),
            ],
          ),
        ),
        _Section(
          title: 'Empty, error, and permission states',
          child: Column(
            children: [
              const EmptyState(
                title: 'No assessments yet',
                message: 'Create your first assessment to start collecting marks.',
                icon: Icons.grading_outlined,
              ),
              const Divider(),
              const ForbiddenView(),
              const Divider(),
              const NotFoundView(),
            ],
          ),
        ),
        _Section(
          title: 'Avatars and skeleton',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: AppSpacing.s8,
                children: const [
                  Avatar(name: 'Grace Wanjiru'),
                  Avatar(name: 'David Otieno'),
                  Avatar(name: 'Faith Achieng'),
                  Avatar(name: 'Kilimani Academy', square: true),
                ],
              ),
              const SizedBox(height: AppSpacing.s16),
              const SkeletonTileRow(),
              const SizedBox(height: AppSpacing.s16),
              const LoadingSkeleton(rows: 3),
            ],
          ),
        ),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.s24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: context.texts.titleMedium),
          const SizedBox(height: AppSpacing.s12),
          child,
          const SizedBox(height: AppSpacing.s8),
          Divider(color: context.tokens.border),
        ],
      ),
    );
  }
}

class _GradientSwatch extends StatelessWidget {
  const _GradientSwatch({required this.name, required this.gradient});

  final String name;
  final Gradient gradient;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 132,
          height: 44,
          decoration: BoxDecoration(
            gradient: gradient,
            borderRadius: AppRadius.mdAll,
            border: Border.all(color: context.tokens.border),
          ),
        ),
        const SizedBox(height: 4),
        Text(name, style: context.texts.labelSmall),
      ],
    );
  }
}

class _Swatch extends StatelessWidget {
  const _Swatch({required this.name, required this.color});

  final String name;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 72,
          height: 44,
          decoration: BoxDecoration(
            color: color,
            borderRadius: AppRadius.mdAll,
            border: Border.all(color: context.tokens.border),
          ),
        ),
        const SizedBox(height: 4),
        Text(name, style: context.texts.labelSmall),
      ],
    );
  }
}
