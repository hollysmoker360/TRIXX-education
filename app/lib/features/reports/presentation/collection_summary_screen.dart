import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Fee collection summary report: invoiced vs collected vs outstanding by period.
class CollectionSummaryScreen extends ConsumerStatefulWidget {
  const CollectionSummaryScreen({super.key});

  @override
  ConsumerState<CollectionSummaryScreen> createState() => _CollectionSummaryScreenState();
}

class _CollectionSummaryScreenState extends ConsumerState<CollectionSummaryScreen> {
  String? _from;
  String? _to;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_from != null) filters['from'] = _from;
    if (_to != null) filters['to'] = _to;

    final report = ref.watch(collectionSummaryProvider(filters));

    return ScreenScaffold(
      title: s.collectionSummary,
      description: s.collectionSummaryDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            _SummaryCard(
              label: s.totalInvoiced,
              value: '\$${data['invoiced'] ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s8),
            _SummaryCard(
              label: s.totalCollected,
              value: '\$${data['collected'] ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s8),
            _SummaryCard(
              label: s.totalOutstanding,
              value: '\$${data['outstanding'] ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.period,
                    primary: true,
                    cell: (context, row) => Text('${row['period']}'),
                  ),
                  AppColumn(
                    label: s.invoiced,
                    numeric: true,
                    cell: (context, row) => Text('\$${row['invoiced']}'),
                  ),
                  AppColumn(
                    label: s.collected,
                    numeric: true,
                    cell: (context, row) => Text('\$${row['collected']}'),
                  ),
                  AppColumn(
                    label: s.outstanding,
                    numeric: true,
                    cell: (context, row) => Text('\$${row['outstanding']}'),
                  ),
                ],
                rows: (data['periods'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['period']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(collectionSummaryProvider(filters))),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(value,
                style: context.texts.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
            Text(label, style: context.texts.bodySmall?.copyWith(color: context.tokens.textSecondary)),
          ],
        ),
      ),
    );
  }
}
