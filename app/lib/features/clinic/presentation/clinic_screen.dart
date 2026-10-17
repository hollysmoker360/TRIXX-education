import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

class ClinicScreen extends ConsumerStatefulWidget {
  const ClinicScreen({super.key});
  @override
  ConsumerState<ClinicScreen> createState() => _ClinicScreenState();
}

class _ClinicScreenState extends ConsumerState<ClinicScreen> {
  String _section = 'visits';

  Map<String, String> _sections(S s) => {
        'visits': 'Visits',
        'appointments': 'Appointments',
        'medications': 'Medications',
      };

  String get _path => '/api/v1/clinic/$_section';

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
                    cell: (ctx, r) => Text('${r['title'] ?? r['name'] ?? r['studentName'] ?? ''}'),
                    primary: true,
                  ),
                  AppColumn<Map<String, dynamic>>(
                    label: 'Status',
                    cell: (ctx, r) => StatusChip(
                      label: '${r['status'] ?? 'RECORDED'}',
                      kind: StatusKind.info,
                    ),
                    width: 120,
                  ),
                ],
                rows: rows,
                rowKey: (r) => '${r['id']}',
                searchHint: 'Search clinic',
                empty: const EmptyState(
                  icon: Icons.local_hospital_outlined,
                  title: 'No clinic records',
                  message: 'Medical visit records appear here (restricted access).',
                ),
              ),
            ),
          ),
        ),
      ]),
    );
  }
}
