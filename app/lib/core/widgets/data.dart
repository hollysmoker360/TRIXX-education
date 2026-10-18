import 'dart:async';

import 'package:flutter/material.dart';

import '../l10n/strings.dart';
import '../theme/tokens.dart';
import 'inputs.dart';
import 'states.dart';

/// A column definition for [AppDataTable].
class AppColumn<T> {
  AppColumn({
    required this.label,
    required this.cell,
    this.numeric = false,
    this.primary = false,
    this.sensitive = false,
    this.sortValue,
    this.width,
  });

  final String label;
  final Widget Function(BuildContext context, T row) cell;

  /// Right-align and use tabular figures.
  final bool numeric;

  /// Shown in the stacked compact record (specification section 7.2).
  final bool primary;

  /// Masked in list rows by default.
  final bool sensitive;

  final String Function(T row)? sortValue;
  final double? width;
}

/// Server-side sort/filter/pagination table that becomes stacked records on
/// compact widths. Sensitive columns are masked in list rows by default.
class AppDataTable<T> extends StatefulWidget {
  const AppDataTable({
    super.key,
    required this.columns,
    required this.rows,
    required this.rowKey,
    this.onTap,
    this.searchHint,
    this.searchText,
    this.onSearch,
    this.loading = false,
    this.error,
    this.onRetry,
    this.empty,
    this.filters,
    this.savedViews,
    this.onSelectView,
    this.header,
    this.footer,
    this.maskSensitive = true,
    this.selectable = false,
    this.selectedKeys = const {},
    this.onSelectionChanged,
    this.bulkActions,
  });

  final List<AppColumn<T>> columns;
  final List<T> rows;
  final String Function(T row) rowKey;
  final void Function(T row)? onTap;
  final String? searchHint;
  final String? searchText;
  final ValueChanged<String>? onSearch;
  final bool loading;
  final Object? error;
  final VoidCallback? onRetry;
  final Widget? empty;
  final List<Widget>? filters;
  final List<String>? savedViews;
  final ValueChanged<String>? onSelectView;
  final Widget? header;
  final Widget? footer;
  final bool maskSensitive;
  final bool selectable;
  final Set<String> selectedKeys;
  final ValueChanged<Set<String>>? onSelectionChanged;
  final List<Widget>? bulkActions;

  @override
  State<AppDataTable<T>> createState() => _AppDataTableState<T>();
}

class _AppDataTableState<T> extends State<AppDataTable<T>> {
  late final TextEditingController _search = TextEditingController(text: widget.searchText);
  final _debouncer = _Debouncer();
  String? _activeView;

  @override
  void dispose() {
    _search.dispose();
    _debouncer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final hasSearch = widget.onSearch != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.header != null) widget.header!,
        if (hasSearch || (widget.filters?.isNotEmpty ?? false) || (widget.savedViews?.isNotEmpty ?? false))
          FilterBar(
            search: hasSearch
                ? AppTextField(
                    controller: _search,
                    label: S.of(context).search,
                    hint: widget.searchHint,
                    prefixIcon: Icons.search,
                    onChanged: (value) => _debouncer.run(() => widget.onSearch!(value)),
                    suffix: _search.text.isEmpty
                        ? null
                        : IconButton(
                            icon: const Icon(Icons.close, size: 18),
                            tooltip: S.of(context).clear,
                            onPressed: () {
                              _search.clear();
                              setState(() {});
                              widget.onSearch!('');
                            },
                          ),
                  )
                : null,
            filters: widget.filters ?? const [],
            savedViews: widget.savedViews,
            activeView: _activeView,
            onSelectView: (view) {
              setState(() => _activeView = view);
              widget.onSelectView?.call(view);
            },
          ),
        if (widget.selectable && widget.selectedKeys.isNotEmpty && widget.bulkActions != null)
          _BulkBar(
            count: widget.selectedKeys.length,
            actions: widget.bulkActions!,
            onClear: () => widget.onSelectionChanged?.call(const {}),
          ),
        if (widget.loading)
          const LoadingSkeleton(rows: 5)
        else if (widget.error != null)
          ErrorView(error: widget.error!, onRetry: widget.onRetry ?? () {})
        else if (widget.rows.isEmpty)
          widget.empty ?? EmptyState(message: S.of(context).emptyList)
        else
          LayoutBuilder(
            builder: (context, constraints) {
              if (constraints.maxWidth < 600) {
                return _stacked(context);
              }
              return _grid(context);
            },
          ),
        if (widget.footer != null) widget.footer!,
      ],
    );
  }

  Widget _grid(BuildContext context) {
    final columns = widget.columns;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: ConstrainedBox(
        constraints: BoxConstraints(minWidth: MediaQuery.sizeOf(context).width.clamp(0, 1200).toDouble()),
        child: DataTable(
          showCheckboxColumn: widget.selectable,
          headingRowHeight: 44,
          dataRowMinHeight: 48,
          dataRowMaxHeight: 64,
          headingTextStyle: context.texts.labelMedium?.copyWith(color: context.tokens.textSecondary),
          dataTextStyle: context.texts.bodyMedium,
          columns: [
            if (widget.selectable)
              DataColumn(label: Text(S.of(context).select), onSort: null),
            for (final column in columns)
              DataColumn(
                numeric: column.numeric,
                label: Text(column.label),
                onSort: column.sortValue == null
                    ? null
                    : (index, ascending) => widget.onSearch?.call(_search.text),
              ),
          ],
          rows: [
            for (final row in widget.rows)
              DataRow(
                key: ValueKey(widget.rowKey(row)),
                selected: widget.selectedKeys.contains(widget.rowKey(row)),
                onSelectChanged: widget.selectable
                    ? (selected) {
                        final next = {...widget.selectedKeys};
                        if (selected ?? false) {
                          next.add(widget.rowKey(row));
                        } else {
                          next.remove(widget.rowKey(row));
                        }
                        widget.onSelectionChanged?.call(next);
                      }
                    : null,
                onLongPress: widget.onTap == null ? null : () => widget.onTap!(row),
                cells: [
                  if (widget.selectable)
                    DataCell(
                      Checkbox(
                        value: widget.selectedKeys.contains(widget.rowKey(row)),
                        onChanged: (selected) {
                          final next = {...widget.selectedKeys};
                          if (selected ?? false) {
                            next.add(widget.rowKey(row));
                          } else {
                            next.remove(widget.rowKey(row));
                          }
                          widget.onSelectionChanged?.call(next);
                        },
                      ),
                    ),
                  for (final column in columns)
                    DataCell(
                      _maybeMask(context, column, row),
                      onTap: widget.onTap == null ? null : () => widget.onTap!(row),
                    ),
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _stacked(BuildContext context) {
    final primary = widget.columns.where((c) => c.primary).toList();
    final shown = primary.isEmpty ? widget.columns.take(2).toList() : primary;
    return ListView.separated(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: widget.rows.length,
      separatorBuilder: (context, index) => Divider(height: 1, color: context.tokens.border),
      itemBuilder: (context, index) {
        final row = widget.rows[index];
        final key = widget.rowKey(row);
        return InkWell(
          onTap: widget.onTap == null ? null : () => widget.onTap!(row),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.s12, horizontal: AppSpacing.s16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (widget.selectable)
                  Checkbox(
                    value: widget.selectedKeys.contains(key),
                    onChanged: (selected) {
                      final next = {...widget.selectedKeys};
                      if (selected ?? false) {
                        next.add(key);
                      } else {
                        next.remove(key);
                      }
                      widget.onSelectionChanged?.call(next);
                    },
                  ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (var i = 0; i < shown.length; i++)
                        Padding(
                          padding: EdgeInsets.only(top: i == 0 ? 0 : AppSpacing.s4),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              if (i != 0)
                                SizedBox(
                                  width: 96,
                                  child: Text(
                                    shown[i].label,
                                    style: context.texts.bodySmall,
                                  ),
                                ),
                              Expanded(child: _maybeMask(context, shown[i], row)),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _maybeMask(BuildContext context, AppColumn<T> column, T row) {
    final child = column.cell(context, row);
    if (!column.sensitive || !widget.maskSensitive) return child;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.visibility_off_outlined, size: 14),
        const SizedBox(width: AppSpacing.s4),
        Text('••••', style: context.texts.bodyMedium, semanticsLabel: S.of(context).masked),
      ],
    );
  }
}

class _BulkBar extends StatelessWidget {
  const _BulkBar({required this.count, required this.actions, required this.onClear});

  final int count;
  final List<Widget> actions;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.s12),
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s12, vertical: AppSpacing.s8),
      decoration: BoxDecoration(
        color: context.scheme.primary.withValues(alpha: 0.10),
        borderRadius: AppRadius.mdAll,
      ),
      child: Row(
        children: [
          Text(S.of(context).selectedCount(count), style: context.texts.labelMedium),
          const Spacer(),
          ...[
            for (final action in actions) ...[action, const SizedBox(width: AppSpacing.s8)],
          ],
          TextButton(onPressed: onClear, child: Text(S.of(context).clearSelection)),
        ],
      ),
    );
  }
}

/// Search box, filter chips, clear all, and saved views (specification 7.1).
class FilterBar extends StatelessWidget {
  const FilterBar({
    super.key,
    this.search,
    this.filters = const [],
    this.savedViews,
    this.activeView,
    this.onSelectView,
    this.onClearAll,
  });

  final Widget? search;
  final List<Widget> filters;
  final List<String>? savedViews;
  final String? activeView;
  final ValueChanged<String>? onSelectView;
  final VoidCallback? onClearAll;

  @override
  Widget build(BuildContext context) {
    final hasViews = savedViews?.isNotEmpty ?? false;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.s12),
      child: Wrap(
        spacing: AppSpacing.s8,
        runSpacing: AppSpacing.s8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          if (search != null)
            SizedBox(
              width: 320,
              child: search!,
            ),
          ...filters,
          if (hasViews) ...[
            for (final view in savedViews!)
              ChoiceChip(
                label: Text(view),
                selected: view == activeView,
                onSelected: (_) => onSelectView?.call(view),
              ),
          ],
          if (filters.isNotEmpty || hasViews)
            TextButton(
              onPressed: onClearAll,
              child: Text(S.of(context).clearAll),
            ),
        ],
      ),
    );
  }
}

class _Debouncer {
  Timer? _timer;

  void run(VoidCallback action, [Duration delay = const Duration(milliseconds: 300)]) {
    _timer?.cancel();
    _timer = Timer(delay, action);
  }

  void dispose() => _timer?.cancel();
}

/// Keyboard-accessible sort header helper for custom tables.
class SortableHeader extends StatelessWidget {
  const SortableHeader({
    super.key,
    required this.label,
    required this.ascending,
    required this.active,
    required this.onTap,
  });

  final String label;
  final bool ascending;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: AppSpacing.s8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(label, style: context.texts.labelMedium),
              if (active)
                Icon(ascending ? Icons.arrow_upward : Icons.arrow_downward, size: 14),
            ],
          ),
        ),
      ),
    );
  }
}
