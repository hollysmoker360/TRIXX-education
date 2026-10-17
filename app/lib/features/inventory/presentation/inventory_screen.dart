import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Inventory & procurement workspace.
class InventoryScreen extends ConsumerStatefulWidget {
  const InventoryScreen({super.key});
  @override
  ConsumerState<InventoryScreen> createState() => _InventoryScreenState();
}

class _InventoryScreenState extends ConsumerState<InventoryScreen> {
  String _section = 'items';

  Map<String, String> _sections(S s) => {
        'items': 'Items',
        'stock': 'Stock levels',
        'purchase-orders': 'Purchase orders',
        'suppliers': 'Suppliers',
      };

  String get _path => '/api/v1/inventory/$_section';

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final provider = recordsProvider(_path);
    return Scaffold(
      body: Column(children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.all(AppSpacing.s16),
          child: Row(children: [
            for (final section in _sections(s).entries)
              Padding(
                padding: const EdgeInsets.only(right: AppSpacing.s8),
                child: ChoiceChip(
                  label: Text(section.value),
                  selected: _section == section.key,
                  onSelected: (_) => setState(() => _section = section.key),
                ),
              ),
          ]),
        ),
        Expanded(
          child: AsyncView<List<Map<String, dynamic>>>(
            value: ref.watch(provider),
            onRetry: () => ref.invalidate(provider),
            isEmpty: (rows) => rows.isEmpty,
            emptyMessage: 'No records yet.',
            builder: (rows) => RefreshIndicator(
              onRefresh: () => ref.refresh(provider.future),
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn<Map<String, dynamic>>(
                    label: 'Name',
                    cell: (ctx, r) => Text('${r['name'] ?? r['title'] ?? ''}'),
                    primary: true,
                  ),
                  AppColumn<Map<String, dynamic>>(
                    label: 'Status',
                    cell: (ctx, r) => StatusChip(
                      label: '${r['status'] ?? 'IN_STOCK'}',
                      kind: _kind('${r['status'] ?? 'IN_STOCK'}'),
                    ),
                    width: 120,
                  ),
                ],
                rows: rows,
                rowKey: (r) => '${r['id']}',
                searchHint: 'Search inventory',
                empty: const EmptyState(
                  icon: Icons.inventory_2_outlined,
                  title: 'No inventory records',
                  message: 'Inventory items appear here.',
                ),
              ),
            ),
          ),
        ),
      ]),
    );
  }

  StatusKind _kind(String status) {
    switch (status.toUpperCase()) {
      case 'IN_STOCK':
        return StatusKind.success;
      case 'LOW_STOCK':
        return StatusKind.warning;
      case 'OUT_OF_STOCK':
        return StatusKind.error;
      default:
        return StatusKind.info;
    }
  }
}
