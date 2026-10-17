import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/strings.dart';
import '../theme/tokens.dart';

/// One entry in the global command palette.
class CommandItem {
  const CommandItem({
    required this.label,
    required this.icon,
    this.subtitle,
    this.keywords = const [],
    this.onSelect,
  });

  final String label;
  final IconData icon;
  final String? subtitle;
  final List<String> keywords;
  final VoidCallback? onSelect;

  bool matches(String query) {
    if (query.isEmpty) return true;
    final q = query.toLowerCase();
    return label.toLowerCase().contains(q) ||
        (subtitle?.toLowerCase().contains(q) ?? false) ||
        keywords.any((k) => k.toLowerCase().contains(q));
  }
}

/// Global jump-and-search palette (Ctrl/Cmd+K) for desktop and web
/// (specification section 5.2). Only offers destinations the user is
/// authorised to reach — the caller filters the list.
Future<void> showCommandPalette(BuildContext context, List<CommandItem> items) {
  return showDialog<void>(
    context: context,
    barrierColor: Colors.black54,
    builder: (context) => _CommandPalette(items: items),
  );
}

class _CommandPalette extends StatefulWidget {
  const _CommandPalette({required this.items});

  final List<CommandItem> items;

  @override
  State<_CommandPalette> createState() => _CommandPaletteState();
}

class _CommandPaletteState extends State<_CommandPalette> {
  final _controller = TextEditingController();
  final _focus = FocusNode();
  int _highlighted = 0;

  List<CommandItem> get _filtered => widget.items.where((i) => i.matches(_controller.text)).toList();

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _select(CommandItem item) {
    Navigator.of(context).pop();
    item.onSelect?.call();
  }

  @override
  Widget build(BuildContext context) {
    final results = _filtered;
    final highlighted = results.isEmpty ? -1 : _highlighted.clamp(0, results.length - 1);

    return Dialog(
      alignment: Alignment.topCenter,
      insetPadding: const EdgeInsets.only(top: 96, left: 24, right: 24, bottom: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.s12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Shortcuts(
                shortcuts: const {
                  SingleActivator(LogicalKeyboardKey.arrowDown): _MoveIntent(1),
                  SingleActivator(LogicalKeyboardKey.arrowUp): _MoveIntent(-1),
                },
                child: Actions(
                  actions: {
                    _MoveIntent: CallbackAction<_MoveIntent>(
                      onInvoke: (intent) {
                        setState(() {
                          _highlighted = (highlighted + intent.delta) % (results.isEmpty ? 1 : results.length);
                          if (_highlighted < 0) _highlighted = results.length - 1;
                        });
                        return null;
                      },
                    ),
                  },
                  child: TextField(
                    controller: _controller,
                    focusNode: _focus,
                    autofocus: true,
                    onChanged: (_) => setState(() => _highlighted = 0),
                    onSubmitted: (_) {
                      if (highlighted >= 0) _select(results[highlighted]);
                    },
                    decoration: InputDecoration(
                      hintText: S.of(context).commandPaletteHint,
                      prefixIcon: const Icon(Icons.search),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.s8),
              if (results.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(AppSpacing.s24),
                  child: Text(
                    S.of(context).noMatches,
                    textAlign: TextAlign.center,
                    style: context.texts.bodyMedium,
                  ),
                )
              else
                Flexible(
                  child: ListView.builder(
                    shrinkWrap: true,
                    itemCount: results.length,
                    itemBuilder: (context, index) {
                      final item = results[index];
                      return ListTile(
                        selected: index == highlighted,
                        leading: Icon(item.icon),
                        title: Text(item.label),
                        subtitle: item.subtitle == null ? null : Text(item.subtitle!),
                        onTap: () => _select(item),
                      );
                    },
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MoveIntent extends Intent {
  const _MoveIntent(this.delta);
  final int delta;
}
