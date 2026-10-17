import 'dart:convert';
import 'dart:typed_data';

import 'package:file_saver/file_saver.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/theme/typography.dart';
import '../../../core/widgets/common.dart';

/// Report card: a printable, guardian-shareable summary of published results and
/// attendance. Only published results appear; corrections create a new audited
/// version on the server.
class ReportCardScreen extends ConsumerWidget {
  const ReportCardScreen({super.key, required this.studentId, this.studentName});

  final String studentId;
  final String? studentName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = S.of(context);
    final session = ref.watch(sessionProvider);
    final student = ref.watch(_studentProvider(studentId));
    final grades = ref.watch(_myGradesProvider);
    final attendance = ref.watch(_attendanceSummaryProvider(studentId));

    return ScreenScaffold(
      title: s.reportCard,
      description: studentName ?? s.students,
      actions: [
        IconButton(
          tooltip: s.export,
          icon: const Icon(Icons.file_download_outlined),
          onPressed: () => _export(context, ref),
        ),
      ],
      primaryAction: AppButton(
        label: s.printReport,
        icon: Icons.print_outlined,
        onPressed: () => showAppSnack(context, s.printReport, kind: StatusKind.info),
      ),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.s24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Document header: school identity plus the Trixx footer mark.
              Row(
                children: [
                  Avatar(name: session.membership?.schoolName ?? 'School', size: 44, square: true),
                  const SizedBox(width: AppSpacing.s16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(session.membership?.schoolName ?? '', style: context.texts.titleLarge),
                        Text(s.reportCard, style: context.texts.bodySmall),
                      ],
                    ),
                  ),
                  Text(
                    DateTime.now().toIso8601String().substring(0, 10),
                    style: context.texts.bodySmall,
                  ),
                ],
              ),
              const Divider(height: AppSpacing.s32),
              student.when(
                data: (data) => Text(
                  '${data['firstName'] ?? ''} ${data['lastName'] ?? ''}'.trim(),
                  style: context.texts.headlineMedium,
                ),
                loading: () => Text(s.loading, style: context.texts.bodyMedium),
                error: (_, _) => Text(studentName ?? '', style: context.texts.headlineMedium),
              ),
              const SizedBox(height: AppSpacing.s16),
              Text(s.published, style: context.texts.titleMedium),
              const SizedBox(height: AppSpacing.s8),
              grades.when(
                data: (rows) => rows.isEmpty
                    ? EmptyState(message: s.emptyList)
                    : Table(
                        border: TableBorder(horizontalInside: BorderSide(color: context.tokens.border)),
                        columnWidths: const {
                          0: FlexColumnWidth(2),
                          1: FlexColumnWidth(),
                          2: FlexColumnWidth(),
                          3: FlexColumnWidth(2),
                        },
                        children: [
                          TableRow(
                            decoration: BoxDecoration(color: context.tokens.canvas),
                            children: [
                              _cell(context, s.subject, bold: true),
                              _cell(context, s.marks, bold: true, numeric: true),
                              _cell(context, s.total, bold: true, numeric: true),
                              _cell(context, s.feedback, bold: true),
                            ],
                          ),
                          for (final row in rows)
                            TableRow(
                              children: [
                                _cell(context, '${row['assignmentTitle'] ?? row['title'] ?? ''}'),
                                _cell(context, '${row['grade'] ?? '—'}', numeric: true),
                                _cell(context, '${row['maxMarks'] ?? '—'}', numeric: true),
                                _cell(context, '${row['feedback'] ?? ''}'),
                              ],
                            ),
                        ],
                      ),
                loading: () => const LoadingSkeleton(rows: 4),
                error: (error, _) => ErrorView(
                  error: error,
                  onRetry: () => ref.invalidate(_myGradesProvider),
                ),
              ),
              const Divider(height: AppSpacing.s32),
              Text(s.attendance, style: context.texts.titleMedium),
              const SizedBox(height: AppSpacing.s8),
              attendance.when(
                data: (data) => Wrap(
                  spacing: AppSpacing.s8,
                  runSpacing: AppSpacing.s8,
                  children: [
                    StatusChip(
                      label: '${s.markPresent} ${data['present'] ?? 0}',
                      kind: StatusKind.success,
                      icon: Icons.check,
                    ),
                    StatusChip(
                      label: '${s.markAbsent} ${data['absent'] ?? 0}',
                      kind: StatusKind.error,
                      icon: Icons.close,
                    ),
                    StatusChip(
                      label: '${s.markLate} ${data['late'] ?? 0}',
                      kind: StatusKind.warning,
                      icon: Icons.schedule,
                    ),
                  ],
                ),
                loading: () => const LoadingSkeleton(rows: 1),
                error: (error, _) => ErrorView(
                  error: error,
                  onRetry: () => ref.invalidate(_attendanceSummaryProvider(studentId)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _cell(BuildContext context, String text, {bool bold = false, bool numeric = false}) => Padding(
        padding: const EdgeInsets.all(AppSpacing.s8),
        child: Text(
          text,
          textAlign: numeric ? TextAlign.end : TextAlign.start,
          style: AppType.numeric(
            context.texts.bodyMedium?.copyWith(
              fontWeight: bold ? FontWeight.w600 : FontWeight.w400,
            ),
          ),
        ),
      );

  Future<void> _export(BuildContext context, WidgetRef ref) async {
    final rows = ref.read(_myGradesProvider).valueOrNull ?? const <Map<String, dynamic>>[];
    final buffer = StringBuffer('subject,marks,total,feedback\n');
    for (final row in rows) {
      buffer.writeln(
        '${row['assignmentTitle'] ?? row['title'] ?? ''},${row['grade'] ?? ''},${row['maxMarks'] ?? ''},"${row['feedback'] ?? ''}"',
      );
    }
    final bytes = Uint8List.fromList(utf8.encode(buffer.toString()));
    await FileSaver.instance.saveFile(
      name: 'report-card-${studentName ?? studentId}',
      bytes: bytes,
      ext: 'csv',
      mimeType: MimeType.csv,
    );
  }
}

final _studentProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/people/students/$studentId');
  return data as Map<String, dynamic>;
});

final _myGradesProvider = FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/learning/assignments/my-grades');
  return (data as List).cast<Map<String, dynamic>>();
});

final _attendanceSummaryProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>((ref, studentId) async {
  final api = ref.watch(apiClientProvider);
  final data = await api.get('/api/v1/attendance/students/$studentId/summary');
  return data as Map<String, dynamic>;
});
