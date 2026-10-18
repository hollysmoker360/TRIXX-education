import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/charts.dart';
import '../../../core/widgets/common.dart';
import 'reports_providers.dart';

/// Expenditure report: supplier expenditure grouped by category.
class ExpenditureScreen extends ConsumerStatefulWidget {
  const ExpenditureScreen({super.key});

  @override
  ConsumerState<ExpenditureScreen> createState() => _ExpenditureScreenState();
}

class _ExpenditureScreenState extends ConsumerState<ExpenditureScreen> {
  String? _from;
  String? _to;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);

    final filters = <String, dynamic>{};
    if (_from != null) filters['from'] = _from;
    if (_to != null) filters['to'] = _to;

    final report = ref.watch(expenditureProvider(filters));

    return ScreenScaffold(
      title: s.expenditure,
      description: s.expenditureDescription,
      embedded: true,
      child: report.when(
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: AppSpacing.s16),
            _SummaryCard(
              label: s.totalExpenditure,
              value: '\$${data['total'] ?? 0}',
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AccessibleChart(
                title: s.expenditureChart,
                summary: s.expenditureSummary,
                rows: _expenditureRows(data),
                chart: SizedBox(
                  height: 200,
                  child: SimpleBarChart(
                    series: _expenditureSeries(data),
                  ),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.s16),
            Expanded(
              child: AppDataTable<Map<String, dynamic>>(
                columns: [
                  AppColumn(
                    label: s.category,
                    primary: true,
                    cell: (context, row) => Text('${row['category']}'),
                  ),
                  AppColumn(
                    label: s.supplier,
                    cell: (context, row) => Text('${row['supplierName']}'),
                  ),
                  AppColumn(
                    label: s.amount,
                    numeric: true,
                    cell: (context, row) => Text('\$${row['amount']}'),
                  ),
                  AppColumn(
                    label: s.invoiceCount,
                    numeric: true,
                    cell: (context, row) => Text('${row['invoiceCount']}'),
                  ),
                ],
                rows: (data['items'] as List? ?? const [])
                    .cast<Map<String, dynamic>>(),
                rowKey: (row) => '${row['category']}-${row['supplierName']}',
              ),
            ),
          ],
        ),
        loading: () => const LoadingSkeleton(rows: 6),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(expenditureProvider(filters))),
      ),
    );
  }

  List<MapEntry<String, String>> _expenditureRows(Map<String, dynamic> data) {
    final List<MapEntry<String, String>> rows = [];
    for (final row in (data['items'] as List? ?? const [])) {
      rows.add(MapEntry('${row['category']}', '\$${row['amount']}'));
    }
    return rows;
  }

  List<MapEntry<String, double>> _expenditureSeries(Map<String, dynamic> data) {
    final List<MapEntry<String, double>> series = [];
    for (final row in (data['items'] as List? ?? const [])) {
      series.add(MapEntry('${row['category']}', (row['amount'] as num? ?? 0).toDouble()));
    }
    return series;
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
