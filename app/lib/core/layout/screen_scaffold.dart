import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../router/app_routes.dart';
import '../theme/tokens.dart';
import '../widgets/states.dart';
import 'page_header.dart';

/// Standard page frame for a route.
///
/// When a screen is hosted inside the [AdaptiveShell] (embedded) it renders its
/// own [PageHeader] and relies on the shell's top bar; when it is pushed as its
/// own route it gets a [Scaffold] with a back button. This keeps a single screen
/// usable in both positions.
class ScreenScaffold extends StatelessWidget {
  const ScreenScaffold({
    super.key,
    required this.title,
    required this.child,
    this.embedded = false,
    this.description,
    this.primaryAction,
    this.secondaryActions = const [],
    this.breadcrumbs = const [],
    this.actions,    this.floatingActionButton,
    this.scrollable = true,
    this.header,
  });

  final String title;
  final Widget child;
  final bool embedded;
  final String? description;
  final Widget? primaryAction;
  final List<Widget> secondaryActions;
  final List<Crumb> breadcrumbs;
  final List<Widget>? actions;
  final Widget? floatingActionButton;
  final bool scrollable;

  /// Replaces the generated [PageHeader] when a page wants a bespoke header —
  /// for example the gradient [HeroHeader] on the landing pages. [title] is
  /// still used by the shell's top bar.
  final Widget? header;

  @override
  Widget build(BuildContext context) {
    if (embedded) {
      return PageBody(
        scrollable: scrollable,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            header ??
                PageHeader(
                  title: title,
                  description: description,
                  primaryAction: primaryAction,
                  secondaryActions: secondaryActions,
                  breadcrumbs: breadcrumbs,
                ),
            child,
          ],
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          tooltip: MaterialLocalizations.of(context).backButtonTooltip,
          onPressed: () => _back(context),
        ),
        actions: actions,
        bottom: description == null
            ? null
            : PreferredSize(
                preferredSize: const Size.fromHeight(28),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(AppSpacing.s16, 0, AppSpacing.s16, AppSpacing.s8),
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Text(description!, style: context.texts.bodySmall),
                  ),
                ),
              ),
      ),
      floatingActionButton: floatingActionButton,
      body: PageBody(
        scrollable: scrollable,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (primaryAction != null || secondaryActions.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.s8),
                child: Wrap(spacing: AppSpacing.s8, children: [
                  ...secondaryActions,
                  ?primaryAction,
                ]),
              ),
            child,
          ],
        ),
      ),
    );
  }

  void _back(BuildContext context) {
    if (Navigator.of(context).canPop()) {
      context.pop();
    } else {
      context.go(AppRoutes.home);
    }
  }
}
