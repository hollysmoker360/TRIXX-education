import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Staff & HR workspace.
class HrScreen extends ConsumerStatefulWidget {
  const HrScreen({super.key});
  @override
  ConsumerState<HrScreen> createState() => _HrScreenState();
}

class _HrScreenState extends ConsumerState<HrScreen> {
  String _section = 'staff';

  Map<String, String> _sections(S s) => {
        'staff': 'Staff',
        'leave': 'Leave',
        'attendance': 'Attendance',
        'payroll': 'Payroll',
      };

  String get _path => '/api/v1/hr/$_section';

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
                    cell: (ctx, r) => Text('${r['firstName'] ?? ''} ${r['lastName'] ?? ''}'.trim()),
                    primary: true,
                  ),
                  AppColumn<Map<String, dynamic>>(
                    label: 'Status',
                    cell: (ctx, r) => StatusChip(
                      label: '${r['status'] ?? 'ACTIVE'}',
                      kind: _kind('${r['status'] ?? 'ACTIVE'}'),
                    ),
                    width: 120,
                  ),
                ],
                rows: rows,
                rowKey: (r) => '${r['id']}',
                searchHint: 'Search staff',
                empty: const EmptyState(
                  icon: Icons.badge_outlined,
                  title: 'No staff records',
                  message: 'Staff records appear here.',
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
      case 'ACTIVE':
        return StatusKind.success;
      case 'ON_LEAVE':
        return StatusKind.pending;
      case 'EXITED':
        return StatusKind.warning;
      default:
        return StatusKind.info;
    }
  }
}
