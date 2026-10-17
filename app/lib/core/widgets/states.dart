import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../l10n/strings.dart';
import '../layout/window_class.dart';
import '../networking/api_problem.dart';
import '../session/session.dart';
import '../theme/tokens.dart';

/// Renders an [AsyncValue] with consistent loading, error, empty, and success
/// states (specification sections 7.1 and 8.1). Every screen that loads data
/// should funnel through this so the states are uniform.
class AsyncView<T> extends StatelessWidget {
  const AsyncView({
    super.key,
    required this.value,
    required this.onRetry,
    required this.builder,
    this.isEmpty,
    this.emptyMessage,
    this.emptyAction,
    this.loading,
  });

  final AsyncValue<T> value;
  final VoidCallback onRetry;
  final Widget Function(T data) builder;
  final bool Function(T data)? isEmpty;
  final String? emptyMessage;
  final Widget? emptyAction;

  /// Optional skeleton that matches the final layout (no blank screen, no jump).
  final Widget? loading;

  @override
  Widget build(BuildContext context) {
    return value.when(
      skipLoadingOnRefresh: true,
      loading: () => loading ?? const LoadingSkeleton(),
      error: (error, _) => ErrorView(error: error, onRetry: onRetry),
      data: (data) {
        if (isEmpty?.call(data) ?? false) {
          return RefreshIndicator(
            onRefresh: () async => onRetry(),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                EmptyState(
                  message: emptyMessage ?? S.of(context).emptyList,
                  action: emptyAction,
                ),
              ],
            ),
          );
        }
        return builder(data);
      },
    );
  }
}

/// Animates a highlight sweep across its child. Under reduced motion the sweep
/// is replaced by a static tint, so nothing moves for users who asked for calm.
class Shimmer extends StatefulWidget {
  const Shimmer({super.key, required this.child, this.duration = const Duration(milliseconds: 1500)});

  final Widget child;
  final Duration duration;

  @override
  State<Shimmer> createState() => _ShimmerState();
}

class _ShimmerState extends State<Shimmer> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: widget.duration,
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.maybeOf(context)?.disableAnimations ?? false) return widget.child;
    final base = context.tokens.border;
    final highlight = context.scheme.surface;
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) => ShaderMask(
        blendMode: BlendMode.srcATop,
        shaderCallback: (bounds) => LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [base, highlight, base],
          stops: const [0.30, 0.50, 0.70],
          transform: _SlidingGradient(_controller.value * 2 - 1),
        ).createShader(bounds),
        child: child,
      ),
      child: widget.child,
    );
  }
}

class _SlidingGradient extends GradientTransform {
  const _SlidingGradient(this.slidePercent);

  final double slidePercent;

  @override
  Matrix4 transform(Rect bounds, {TextDirection? textDirection}) =>
      Matrix4.translationValues(bounds.width * slidePercent * 2, 0, 0);
}

/// One placeholder block. Compose these to mirror the shape of the final layout
/// so the page never jumps when data arrives.
class SkeletonBlock extends StatelessWidget {
  const SkeletonBlock({
    super.key,
    this.width,
    this.height = 14,
    this.radius = AppRadius.sm,
  });

  final double? width;
  final double height;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: context.tokens.border,
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}

/// A neutral placeholder that matches the final layout shape.
class LoadingSkeleton extends StatelessWidget {
  const LoadingSkeleton({
    super.key,
    this.rows = 6,
    this.padding = const EdgeInsets.all(AppSpacing.s16),
    this.height = 14,
  });

  final int rows;
  final EdgeInsets padding;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: S.of(context).loading,
      liveRegion: true,
      child: Padding(
        padding: padding,
        child: Shimmer(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < rows; i++) ...[
                FractionallySizedBox(
                  alignment: Alignment.centerLeft,
                  widthFactor: i.isEven ? 1 : 0.72,
                  child: SkeletonBlock(height: height),
                ),
                const SizedBox(height: AppSpacing.s12),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Skeleton shaped like a row of KPI tiles — the loading state for dashboards.
class SkeletonTileRow extends StatelessWidget {
  const SkeletonTileRow({super.key, this.count = 3, this.height = 108});

  final int count;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Shimmer(
      child: Row(
        children: [
          for (var i = 0; i < count; i++) ...[
            if (i > 0) const SizedBox(width: AppSpacing.s12),
            Expanded(child: SkeletonBlock(height: height, radius: AppRadius.lg)),
          ],
        ],
      ),
    );
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final problem = error is ApiProblem ? error as ApiProblem : null;
    final offline = problem?.isOffline ?? false;
    final message = problem?.detail ?? '$error';
    final correlation = problem?.code;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                offline ? Icons.cloud_off : Icons.error_outline,
                size: 44,
                color: offline ? context.tokens.textSecondary : context.scheme.error,
              ),
              const SizedBox(height: AppSpacing.s12),
              Text(
                offline ? s.networkError : s.somethingWentWrong,
                textAlign: TextAlign.center,
                style: context.texts.titleMedium,
              ),
              const SizedBox(height: AppSpacing.s4),
              Text(message, textAlign: TextAlign.center, style: context.texts.bodyMedium),
              if (correlation != null && correlation != 'error' && correlation != 'network_error') ...[
                const SizedBox(height: AppSpacing.s8),
                SelectableText(
                  '${s.referenceLabel}: $correlation',
                  style: context.texts.bodySmall,
                  textAlign: TextAlign.center,
                ),
              ],
              const SizedBox(height: AppSpacing.s16),
              FilledButton.tonalIcon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh, size: 18),
                label: Text(s.retry),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 403: explains who can grant access, without leaking any data (section 8.1).
class ForbiddenView extends StatelessWidget {
  const ForbiddenView({super.key, this.message, this.onBack});

  final String? message;
  final VoidCallback? onBack;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.lock_outline, size: 44, color: context.tokens.warning),
              const SizedBox(height: AppSpacing.s12),
              Text(s.accessDenied, textAlign: TextAlign.center, style: context.texts.titleMedium),
              const SizedBox(height: AppSpacing.s4),
              Text(
                message ?? s.accessDeniedHelp,
                textAlign: TextAlign.center,
                style: context.texts.bodyMedium,
              ),
              if (onBack != null) ...[
                const SizedBox(height: AppSpacing.s16),
                OutlinedButton(onPressed: onBack, child: Text(s.goBack)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// 404: a not-found page that does not reveal whether the record exists in
/// another school (section 11.2).
class NotFoundView extends StatelessWidget {
  const NotFoundView({super.key, this.onBack});

  final VoidCallback? onBack;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.search_off, size: 44, color: context.tokens.textSecondary),
            const SizedBox(height: AppSpacing.s12),
            Text(s.notFound, style: context.texts.titleMedium),
            const SizedBox(height: AppSpacing.s4),
            Text(s.notFoundHelp, textAlign: TextAlign.center, style: context.texts.bodyMedium),
            if (onBack != null) ...[
              const SizedBox(height: AppSpacing.s16),
              OutlinedButton(onPressed: onBack, child: Text(s.goBack)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Empty state that invites the next useful action (specification section 2.7).
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.message,
    this.icon = Icons.inbox_outlined,
    this.action,
    this.title,
  });

  final String message;
  final IconData icon;
  final Widget? action;
  final String? title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.s48, horizontal: AppSpacing.s24),
      child: Column(
        children: [
          Icon(icon, size: 44, color: context.tokens.textSecondary),
          const SizedBox(height: AppSpacing.s12),
          if (title != null) ...[
            Text(title!, style: context.texts.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.s4),
          ],
          Text(message, style: context.texts.bodyMedium, textAlign: TextAlign.center),
          if (action != null) ...[
            const SizedBox(height: AppSpacing.s16),
            action!,
          ],
        ],
      ),
    );
  }
}

/// Presentation-only permission gate (specification section 7.1). Hiding a
/// control never replaces backend enforcement; it only avoids offering actions
/// the user cannot complete.
class PermissionGate extends ConsumerWidget {
  const PermissionGate({
    super.key,
    this.permission,
    this.anyOf = const [],
    this.child,
    this.fallback,
    this.disableInsteadOfHide = false,
    required this.builder,
  });

  final String? permission;
  final List<String> anyOf;
  final Widget? child;
  final Widget? fallback;

  /// When true the child renders but is wrapped so taps have no effect and it is
  /// visually de-emphasised. Useful for "you can see it but not use it" cases.
  final bool disableInsteadOfHide;

  final Widget Function(BuildContext context, bool allowed)? builder;

  bool _allowed(SessionState session) {
    if (permission != null && session.can(permission!)) return true;
    if (anyOf.isNotEmpty && anyOf.any(session.can)) return true;
    return false;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final allowed = _allowed(ref.watch(sessionProvider));
    if (builder != null) return builder!(context, allowed);
    if (allowed) return child!;
    if (disableInsteadOfHide && child != null) {
      return Opacity(
        opacity: 0.5,
        child: IgnorePointer(child: child),
      );
    }
    return fallback ?? const SizedBox.shrink();
  }
}

/// Scrollable page body with consistent gutters and a capped content width so
/// lines and charts stay readable on very large screens (section 5.1).
class PageBody extends StatelessWidget {
  const PageBody({
    super.key,
    required this.child,
    this.scrollable = true,
    this.padding,
    this.maxWidth = AppLayout.maxContentWidth,
  });

  final Widget child;
  final bool scrollable;
  final EdgeInsets? padding;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final window = windowClassOfContext(context);
    final effectivePadding = padding ?? EdgeInsets.all(window.gutter);
    final content = Padding(padding: effectivePadding, child: child);
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: scrollable
            ? SingleChildScrollView(child: content)
            : content,
      ),
    );
  }
}
