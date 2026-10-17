import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/record_editor.dart';

/// Finance & payments workspace (spec: Finance and payments).
class FinanceScreen extends ConsumerStatefulWidget {
  const FinanceScreen({super.key});
  @override
  ConsumerState<FinanceScreen> createState() => _FinanceScreenState();
}

class _FinanceScreenState extends ConsumerState<FinanceScreen> {
  String _section = 'invoices';

  Map<String, String> _sections(S s) => {
        'invoices': 'Invoices',
        'payments': 'Payments',
        'statements': 'Statements',
        'reconciliation': 'Reconciliation',
      };

  String get _path => '/api/v1/finance/$_section';

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
              child: _buildList(context, rows),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _buildList(BuildContext context, List<Map<String, dynamic>> rows) {
    switch (_section) {
      case 'invoices':
        return _invoicesList(context, rows);
      case 'payments':
        return _paymentsList(context, rows);
      case 'statements':
        return _statementsList(context, rows);
      default:
        return _reconciliationList(context, rows);
    }
  }

  Widget _invoicesList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Reference',
          cell: (ctx, r) => Text('${r['reference'] ?? r['title'] ?? r['id'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'PENDING'}',
            kind: _paymentKind('${r['status'] ?? 'PENDING'}'),
          ),
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Amount',
          cell: (ctx, r) => Text('${r['amount'] ?? ''}'),
          numeric: true,
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Due',
          cell: (ctx, r) => Text('${r['dueAt'] ?? r['dueDate'] ?? ''}'),
          width: 140,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search invoices',
      empty: const EmptyState(
        icon: Icons.receipt_long_outlined,
        title: 'No invoices yet',
        message: 'Invoices appear here once fee structures are configured.',
      ),
    );
  }

  Widget _paymentsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Reference',
          cell: (ctx, r) => Text('${r['reference'] ?? r['id'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'PENDING'}',
            kind: _paymentKind('${r['status'] ?? 'PENDING'}'),
          ),
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Amount',
          cell: (ctx, r) => Text('${r['amount'] ?? ''}'),
          numeric: true,
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Paid at',
          cell: (ctx, r) => Text('${r['paidAt'] ?? ''}'),
          width: 160,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search payments',
      empty: const EmptyState(
        icon: Icons.payments_outlined,
        title: 'No payments yet',
        message: 'Payments appear here once transactions are recorded.',
      ),
    );
  }

  Widget _statementsList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Student',
          cell: (ctx, r) => Text('${r['studentName'] ?? r['name'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Balance',
          cell: (ctx, r) => Text('${r['balance'] ?? r['outstanding'] ?? ''}'),
          numeric: true,
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id'] ?? r['studentId']}',
      searchHint: 'Search statements',
      empty: const EmptyState(
        icon: Icons.account_balance_wallet_outlined,
        title: 'No statements yet',
        message: 'Fee statements appear here per student.',
      ),
    );
  }

  Widget _reconciliationList(BuildContext context, List<Map<String, dynamic>> rows) {
    return AppDataTable<Map<String, dynamic>>(
      columns: [
        AppColumn<Map<String, dynamic>>(
          label: 'Reference',
          cell: (ctx, r) => Text('${r['reference'] ?? r['id'] ?? ''}'),
          primary: true,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Status',
          cell: (ctx, r) => StatusChip(
            label: '${r['status'] ?? 'PENDING'}',
            kind: _paymentKind('${r['status'] ?? 'PENDING'}'),
          ),
          width: 120,
        ),
        AppColumn<Map<String, dynamic>>(
          label: 'Amount',
          cell: (ctx, r) => Text('${r['amount'] ?? ''}'),
          numeric: true,
          width: 120,
        ),
      ],
      rows: rows,
      rowKey: (r) => '${r['id']}',
      searchHint: 'Search reconciliations',
      empty: const EmptyState(
        icon: Icons.rule_outlined,
        title: 'No reconciliation items',
        message: 'Unmatched payments appear here for review.',
      ),
    );
  }

  StatusKind _paymentKind(String status) {
    switch (status.toUpperCase()) {
      case 'PAID':
      case 'CONFIRMED':
        return StatusKind.success;
      case 'PENDING':
      case 'INITIATED':
        return StatusKind.pending;
      case 'FAILED':
      case 'OVERDUE':
        return StatusKind.error;
      case 'REFUNDED':
      case 'CANCELLED':
        return StatusKind.warning;
      default:
        return StatusKind.info;
    }
  }
}
