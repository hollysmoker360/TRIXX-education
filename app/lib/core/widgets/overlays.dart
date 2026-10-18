import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../layout/window_class.dart';
import '../theme/tokens.dart';

/// Consequence-aware confirmation (specification section 8.4).
///
/// [typeToConfirm] requires the user to type a record name for irreversible
/// actions on important records. Use an undo snackbar instead of this dialog
/// for reversible actions.
Future<bool> showConfirmDialog(
  BuildContext context, {
  required String title,
  required String message,
  String? confirmLabel,
  String? cancelLabel,
  bool destructive = false,
  String? typeToConfirm,
  Widget? extra,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => _ConfirmDialog(
      title: title,
      message: message,
      confirmLabel: confirmLabel,
      cancelLabel: cancelLabel,
      destructive: destructive,
      typeToConfirm: typeToConfirm,
      extra: extra,
    ),
  );
  return result ?? false;
}

class _ConfirmDialog extends StatefulWidget {
  const _ConfirmDialog({
    required this.title,
    required this.message,
    required this.confirmLabel,
    required this.cancelLabel,
    required this.destructive,
    required this.typeToConfirm,
    required this.extra,
  });

  final String title;
  final String message;
  final String? confirmLabel;
  final String? cancelLabel;
  final bool destructive;
  final String? typeToConfirm;
  final Widget? extra;

  @override
  State<_ConfirmDialog> createState() => _ConfirmDialogState();
}

class _ConfirmDialogState extends State<_ConfirmDialog> {
  final _typed = TextEditingController();

  @override
  void dispose() {
    _typed.dispose();
    super.dispose();
  }

  bool get _canConfirm =>
      widget.typeToConfirm == null || _typed.text.trim() == widget.typeToConfirm;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return AlertDialog(
      title: Text(widget.title),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(widget.message, style: context.texts.bodyMedium),
          if (widget.extra != null) ...[
            const SizedBox(height: AppSpacing.s12),
            widget.extra!,
          ],
          if (widget.typeToConfirm != null) ...[
            const SizedBox(height: AppSpacing.s16),
            Text(s.typeToConfirm(widget.typeToConfirm!), style: context.texts.bodySmall),
            const SizedBox(height: AppSpacing.s8),
            TextField(
              controller: _typed,
              autofocus: true,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(hintText: widget.typeToConfirm),
            ),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(widget.cancelLabel ?? s.cancel),
        ),
        FilledButton(
          onPressed: _canConfirm ? () => Navigator.of(context).pop(true) : null,
          style: widget.destructive
              ? FilledButton.styleFrom(
                  backgroundColor: context.scheme.error,
                  foregroundColor: context.scheme.onError,
                )
              : null,
          child: Text(widget.confirmLabel ?? s.confirm),
        ),
      ],
    );
  }
}

/// Contextual detail or edit panel: a side sheet on large widths, full screen on
/// compact (specification section 7.1).
Future<T?> showSidePanel<T>(
  BuildContext context, {
  required String title,
  required Widget child,
  String? subtitle,
  List<Widget> actions = const [],
  double width = 480,
}) {
  final window = windowClassOfContext(context);
  final fullScreen = window.isCompact;
  return showGeneralDialog<T>(
    context: context,
    barrierDismissible: true,
    barrierLabel: MaterialLocalizations.of(context).modalBarrierDismissLabel,
    barrierColor: Colors.black54,
    transitionDuration: AppMotion.pageTransition,
    pageBuilder: (context, animation, secondaryAnimation) {
      final panel = SafeArea(
        child: Material(
          color: context.tokens.surfaceElevated,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(AppSpacing.s16, AppSpacing.s12, AppSpacing.s8, AppSpacing.s12),
                child: Row(
                  children: [
                    if (fullScreen)
                      IconButton(
                        icon: const Icon(Icons.arrow_back),
                        tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                        onPressed: () => Navigator.of(context).maybePop(),
                      ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(title, style: context.texts.titleLarge),
                          if (subtitle != null) Text(subtitle, style: context.texts.bodySmall),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
                      onPressed: () => Navigator.of(context).maybePop(),
                    ),
                  ],
                ),
              ),
              Divider(height: 1, color: context.tokens.border),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(AppSpacing.s16),
                  child: child,
                ),
              ),
              if (actions.isNotEmpty)
                Container(
                  padding: const EdgeInsets.all(AppSpacing.s12),
                  decoration: BoxDecoration(
                    border: Border(top: BorderSide(color: context.tokens.border)),
                  ),
                  child: Wrap(spacing: AppSpacing.s8, alignment: WrapAlignment.end, children: actions),
                ),
            ],
          ),
        ),
      );
      if (fullScreen) return panel;
      return Align(
        alignment: Alignment.centerRight,
        child: SizedBox(width: width, child: panel),
      );
    },
    transitionBuilder: (context, animation, _, child) {
      final curved = CurvedAnimation(parent: animation, curve: Curves.easeOutCubic);
      return fullScreen
          ? FadeTransition(opacity: curved, child: child)
          : SlideTransition(
              position: Tween<Offset>(begin: const Offset(1, 0), end: Offset.zero).animate(curved),
              child: child,
            );
    },
  );
}

/// Confirms a mutation using the same verb as the action and offers undo for
/// reversible operations (specification sections 2.7 and 8.4).
void showAppSnack(
  BuildContext context,
  String message, {
  String? undoLabel,
  VoidCallback? onUndo,
  StatusKind kind = StatusKind.success,
}) {
  final messenger = ScaffoldMessenger.maybeOf(context);
  if (messenger == null) return;
  final color = context.tokens.status(kind, context.scheme);
  messenger.showSnackBar(
    SnackBar(
      content: Row(
        children: [
          Icon(
            kind == StatusKind.error ? Icons.error_outline : Icons.check_circle_outline,
            size: 18,
            color: color,
          ),
          const SizedBox(width: AppSpacing.s8),
          Expanded(child: Text(message)),
        ],
      ),
      action: onUndo == null
          ? null
          : SnackBarAction(label: undoLabel ?? S.of(context).undo, onPressed: onUndo),
    ),
  );
}
