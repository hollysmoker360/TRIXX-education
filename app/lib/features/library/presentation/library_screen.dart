import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

class LibraryScreen extends ConsumerStatefulWidget {
  const LibraryScreen({super.key});
  @override
  ConsumerState<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends ConsumerState<LibraryScreen> {
  String _section = 'catalogue';

  Map<String, String> _sections(S s) => {
        'catalogue': 'Catalogue',
        'issues': 'Issues & returns',
        'reservations': 'Reservations',
        'overdue': 'Overdue',
      };

  String get _path => '/api/v1/library/$_section';

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
                    label: 'Title',
                    cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? ''}'),
                    primary: true,
                  ),
                  AppColumn<Map<String, dynamic>>(
                    label: 'Status',
                    cell: (ctx, r) => StatusChip(
                      label: '${r['status'] ?? 'AVAILABLE'}',
                      kind: _kind('${r['status'] ?? 'AVAILABLE'}'),
                    ),
                    width: 120,
                  ),
                ],
                rows: rows,
                rowKey: (r) => '${r['id']}',
                searchHint: 'Search library',
                empty: const EmptyState(
                  icon: Icons.menu_book_outlined,
                  title: 'No library records yet',
                  message: 'Catalogue items appear here.',
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
      case 'AVAILABLE':
      case 'RETURNED':
        return StatusKind.success;
      case 'ISSUED':
      case 'RESERVED':
        return StatusKind.pending;
      case 'OVERDUE':
        return StatusKind.error;
      default:
        return StatusKind.info;
    }
  }
}
