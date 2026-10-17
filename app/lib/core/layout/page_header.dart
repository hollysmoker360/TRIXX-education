import 'package:flutter/material.dart';

import '../theme/tokens.dart';
import 'window_class.dart';

/// Breadcrumb entry: a label and an optional tap target.
class Crumb {
  const Crumb(this.label, {this.onTap});
  final String label;
  final VoidCallback? onTap;
}

/// Page header: title, breadcrumbs, one-line description, and the primary
/// action. Every page follows this order so users learn it once
/// (specification sections 5.4 and 7.1).
class PageHeader extends StatelessWidget {
  const PageHeader({
    super.key,
    required this.title,
    this.description,
    this.primaryAction,
    this.breadcrumbs = const [],
    this.onBack,
    this.secondaryActions = const [],
    this.showBackOnAllSizes = false,
  });

  final String title;
  final String? description;
  final Widget? primaryAction;
  final List<Crumb> breadcrumbs;
  final VoidCallback? onBack;
  final List<Widget> secondaryActions;
  final bool showBackOnAllSizes;

  @override
  Widget build(BuildContext context) {
    final window = windowClassOfContext(context);
    final showBack = onBack != null && (showBackOnAllSizes || window.isCompact);
    final showCrumbs = breadcrumbs.isNotEmpty && window.showsBreadcrumbs;

    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.s16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (showCrumbs)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.s8),
              child: Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  for (var i = 0; i < breadcrumbs.length; i++) ...[
                    if (i > 0)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s4),
                        child: Icon(Icons.chevron_right, size: 16, color: context.tokens.textSecondary),
                      ),
                    _CrumbLink(crumb: breadcrumbs[i], isLast: i == breadcrumbs.length - 1),
                  ],
                ],
              ),
            ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (showBack)
                IconButton(
                  onPressed: onBack,
                  icon: const Icon(Icons.arrow_back),
                  tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Semantics(
                      header: true,
                      child: Text(
                        title,
                        style: window.isCompact
                            ? context.texts.titleLarge
                            : context.texts.headlineMedium,
                      ),
                    ),
                    if (description != null) ...[
                      const SizedBox(height: AppSpacing.s4),
                      Text(
                        description!,
                        style: context.texts.bodyMedium?.copyWith(color: context.tokens.textSecondary),
                      ),
                    ],
                  ],
                ),
              ),
              if (secondaryActions.isNotEmpty || primaryAction != null) ...[
                const SizedBox(width: AppSpacing.s16),
                Wrap(
                  spacing: AppSpacing.s8,
                  children: [...secondaryActions, ?primaryAction],
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _CrumbLink extends StatelessWidget {
  const _CrumbLink({required this.crumb, required this.isLast});

  final Crumb crumb;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final style = context.texts.bodySmall?.copyWith(
      color: isLast ? context.scheme.onSurface : context.tokens.textSecondary,
      fontWeight: isLast ? FontWeight.w600 : FontWeight.w400,
    );
    if (crumb.onTap == null || isLast) {
      return Text(crumb.label, style: style);
    }
    return InkWell(
      onTap: crumb.onTap,
      borderRadius: AppRadius.smAll,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s4, vertical: 2),
        child: Text(crumb.label, style: style),
      ),
    );
  }
}
