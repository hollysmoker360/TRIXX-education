import 'dart:typed_data';

import 'package:file_saver/file_saver.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/networking/api_problem.dart';
import '../../../core/session/session.dart';
import '../../../core/widgets/common.dart';

final _classesProvider = FutureProvider.autoDispose((ref) async {
  final data = await ref.watch(apiClientProvider).get('/api/v1/academics/classes');
  return (data as List).cast<Map<String, dynamic>>();
});

class ReportsScreen extends ConsumerStatefulWidget {
  const ReportsScreen({super.key});

  @override
  ConsumerState<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends ConsumerState<ReportsScreen> {
  String? _classGroupId;
  DateTime _from = DateTime.now().subtract(const Duration(days: 30));
  DateTime _to = DateTime.now();
  Map<String, dynamic>? _report;
  bool _loading = false;
  Object? _error;

  static String _key(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-'
      '${d.month.toString().padLeft(2, '0')}-'
      '${d.day.toString().padLeft(2, '0')}';

  Future<void> _load() async {
    final classGroupId = _classGroupId;
    if (classGroupId == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final report = await ref.read(apiClientProvider).get(
        '/api/v1/attendance/reports/class',
        query: {
          'classGroupId': classGroupId,
          'from': _key(_from),
          'to': _key(_to),
        },
      ) as Map<String, dynamic>;
      setState(() => _report = report);
    } on ApiProblem catch (e) {
      setState(() => _error = e);
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _export(String format) async {
    final s = S.of(context);
    final classGroupId = _classGroupId;
    if (classGroupId == null) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final bytes = await ref.read(apiClientProvider).getBytes(
        '/api/v1/attendance/reports/class.$format',
        query: {
          'classGroupId': classGroupId,
          'from': _key(_from),
          'to': _key(_to),
        },
      );
      await FileSaver.instance.saveFile(
        name: 'attendance-${_key(_from)}-${_key(_to)}',
        bytes: Uint8List.fromList(bytes),
        ext: format,
        mimeType: format == 'pdf' ? MimeType.pdf : MimeType.csv,
      );
      messenger.showSnackBar(SnackBar(content: Text(s.download)));
    } on ApiProblem catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.detail)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final classes = ref.watch(_classesProvider);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Wrap(
            spacing: 12,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              SizedBox(
                width: 220,
                child: classes.when(
                  loading: () => const LinearProgressIndicator(),
                  error: (e, _) => Text('$e'),
                  data: (rows) => DropdownButtonFormField<String>(
                    initialValue: _classGroupId,
                    decoration: InputDecoration(
                      labelText: S.of(context).classLabel,
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                    items: [
                      for (final c in rows)
                        DropdownMenuItem(
                            value: '${c['id']}', child: Text('${c['name']}')),
                    ],
                    onChanged: (v) {
                      setState(() => _classGroupId = v);
                      _load();
                    },
                  ),
                ),
              ),
              _DateField(
                label: S.of(context).fromDate,
                value: _from,
                onPicked: (d) {
                  setState(() => _from = d);
                  _load();
                },
              ),
              _DateField(
                label: S.of(context).toDate,
                value: _to,
                onPicked: (d) {
                  setState(() => _to = d);
                  _load();
                },
              ),
              OutlinedButton.icon(
                onPressed: _classGroupId == null ? null : () => _export('csv'),
                icon: const Icon(Icons.table_view_outlined),
                label: const Text('CSV'),
              ),
              OutlinedButton.icon(
                onPressed: _classGroupId == null ? null : () => _export('pdf'),
                icon: const Icon(Icons.picture_as_pdf_outlined),
                label: const Text('PDF'),
              ),
            ],
          ),
        ),
        if (_loading) const LinearProgressIndicator(),
        Expanded(child: _body(s)),
      ],
    );
  }

  Widget _body(S s) {
    if (_error != null) {
      return ErrorView(error: _error!, onRetry: _load);
    }
    final report = _report;
    if (report == null) {
      return EmptyState(
        message: _classGroupId == null
            ? 'Pick a class to build the attendance report.'
            : s.emptyList,
        icon: Icons.insights_outlined,
      );
    }
    final rows = (report['rows'] as List? ?? const [])
        .cast<Map<String, dynamic>>();
    final totals = report['totals'] as Map<String, dynamic>? ?? const {};
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      children: [
        SectionCard(
          title: '${report['from']} → ${report['to']}',
          child: Wrap(
            spacing: 8,
            children: [
              StatusPill(label: '${s.markPresent} ${totals['present'] ?? 0}'),
              StatusPill(
                  label: '${s.markAbsent} ${totals['absent'] ?? 0}',
                  color: Colors.red.shade700),
              StatusPill(
                  label: '${s.markLate} ${totals['late'] ?? 0}',
                  color: Colors.orange.shade700),
              StatusPill(
                  label: '${s.markExcused} ${totals['excused'] ?? 0}',
                  color: Colors.blueGrey),
            ],
          ),
        ),
        const SizedBox(height: 12),
        if (rows.isEmpty)
          EmptyState(message: s.emptyList)
        else
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: DataTable(
              columns: [
                DataColumn(label: Text(s.student)),
                DataColumn(label: Text(s.admShort)),
                DataColumn(label: Text('P'), numeric: true),
                DataColumn(label: Text('A'), numeric: true),
                DataColumn(label: Text('L'), numeric: true),
                DataColumn(label: Text('E'), numeric: true),
                DataColumn(label: Text(s.rate), numeric: true),
              ],
              rows: [
                for (final row in rows)
                  DataRow(cells: [
                    DataCell(Text('${row['studentName']}')),
                    DataCell(Text('${row['admissionNumber']}')),
                    DataCell(Text('${row['present']}')),
                    DataCell(Text('${row['absent']}')),
                    DataCell(Text('${row['late']}')),
                    DataCell(Text('${row['excused']}')),
                    DataCell(Text(
                        '${((row['attendanceRate'] as num?) ?? 0).toStringAsFixed(1)}%')),
                  ]),
              ],
            ),
          ),
      ],
    );
  }
}

class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.onPicked,
  });

  final String label;
  final DateTime value;
  final ValueChanged<DateTime> onPicked;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      icon: const Icon(Icons.event, size: 18),
      label: Text('$label ${_ReportsScreenState._key(value)}'),
      onPressed: () async {
        final picked = await showDatePicker(
          context: context,
          initialDate: value,
          firstDate: DateTime.now().subtract(const Duration(days: 730)),
          lastDate: DateTime.now().add(const Duration(days: 30)),
        );
        if (picked != null) onPicked(picked);
      },
    );
  }
}
