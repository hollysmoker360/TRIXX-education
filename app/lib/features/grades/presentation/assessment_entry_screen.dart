import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/l10n/strings.dart';
import '../../../core/layout/screen_scaffold.dart';
import '../../../core/session/session.dart';
import '../../../core/theme/tokens.dart';
import '../../../core/widgets/common.dart';

/// Create or edit an assessment with components and draft grade entries.
class AssessmentEntryScreen extends ConsumerStatefulWidget {
  const AssessmentEntryScreen({super.key, this.classId});

  final String? classId;

  @override
  ConsumerState<AssessmentEntryScreen> createState() => _AssessmentEntryScreenState();
}

class _AssessmentEntryScreenState extends ConsumerState<AssessmentEntryScreen> {
  final _form = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _maxMarks = TextEditingController();
  final _weight = TextEditingController(text: '1');
  final _dueDate = TextEditingController();
  String _type = 'test';
  DateTime? _dateAssessed;
  final _components = <Map<String, String>>[];

  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _dateAssessed = DateTime.now();
    _components.add({'name': 'Main', 'maxMarks': '', 'weight': '1'});
  }

  @override
  void dispose() {
    _title.dispose();
    _maxMarks.dispose();
    _weight.dispose();
    _dueDate.dispose();
    super.dispose();
  }

  void _addComponent() {
    setState(() {
      _components.add({'name': '', 'maxMarks': '', 'weight': '1'});
    });
  }

  void _removeComponent(int index) {
    setState(() {
      _components.removeAt(index);
    });
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    final savedMessage = S.of(context).assessmentSaved;
    setState(() => _loading = true);
    try {
      final api = ref.read(apiClientProvider);
      final components = _components
          .where((c) => c['name'] != null && c['name']!.isNotEmpty)
          .map((c) => <String, dynamic>{
                'name': c['name']!,
                'maxMarks': double.tryParse(c['maxMarks'] ?? '') ?? 0,
                'weight': double.tryParse(c['weight'] ?? '1') ?? 1,
                'position': _components.indexOf(c),
              })
          .toList();

      await api.post('/api/v1/grades/assessments', data: {
        'classGroupId': widget.classId,
        'subjectId': null,
        'termId': null,
        'title': _title.text.trim(),
        'type': _type,
        'maxMarks': double.tryParse(_maxMarks.text) ?? 0,
        'weight': double.tryParse(_weight.text) ?? 1,
        'dateAssessed': _dateAssessed?.toIso8601String(),
        'dueDate': _dueDate.text.trim().isEmpty ? null : _dueDate.text.trim(),
        'components': components,
      });
      if (mounted) {
        showAppSnack(context, savedMessage, kind: StatusKind.success);
        context.pop();
      }
    } on Object catch (e) {
      if (mounted) showApiError(context, e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _buildComponentRow(Map<String, String> c, int i) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.s12),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: AppTextField(
                    label: S.of(context).componentName,
                    hint: S.of(context).componentName,
                    onChanged: (v) => c['name'] = v,
                  ),
                ),
                const SizedBox(width: AppSpacing.s8),
                SizedBox(
                  width: 110,
                  child: AppTextField(
                    label: S.of(context).maxMarks,
                    hint: S.of(context).maxMarks,
                    keyboardType: TextInputType.number,
                    onChanged: (v) => c['maxMarks'] = v,
                  ),
                ),
                const SizedBox(width: AppSpacing.s8),
                SizedBox(
                  width: 80,
                  child: AppTextField(
                    label: S.of(context).weight,
                    hint: S.of(context).weight,
                    keyboardType: TextInputType.number,
                    onChanged: (v) => c['weight'] = v,
                  ),
                ),
                const SizedBox(width: AppSpacing.s8),
                IconButton(
                  icon: const Icon(Icons.remove_circle_outline),
                  onPressed: _components.length > 1 ? () => _removeComponent(i) : null,
                  tooltip: S.of(context).removeComponent,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return ScreenScaffold(
      title: widget.classId == null ? s.editAssessment : s.createAssessment,
      description: s.assessmentDescription,
      embedded: true,
      primaryAction: AppButton(
        label: s.save,
        variant: AppButtonVariant.primary,
        icon: Icons.save,
        onPressed: _loading ? null : _save,
      ),
      child: Form(
        key: _form,
        child: ListView(
          children: [
            Padding(
              padding: const EdgeInsets.all(AppSpacing.s16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextFormField(
                    controller: _title,
                    decoration: InputDecoration(
                      labelText: s.assessmentTitle,
                      hintText: s.assessmentTitleHint,
                    ),
                    validator: (v) => (v == null || v.isEmpty)
                        ? s.fieldRequired(s.assessmentTitle)
                        : null,
                  ),
                  const SizedBox(height: AppSpacing.s12),
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: _type,
                          decoration: InputDecoration(labelText: s.assessmentType),
                          items: [
                            DropdownMenuItem(value: 'test', child: Text(s.typeTest)),
                            DropdownMenuItem(value: 'quiz', child: Text(s.typeQuiz)),
                            DropdownMenuItem(value: 'exam', child: Text(s.typeExam)),
                            DropdownMenuItem(
                                value: 'practical', child: Text(s.typePractical)),
                            DropdownMenuItem(value: 'project', child: Text(s.typeProject)),
                          ],
                          onChanged: (v) => setState(() => _type = v ?? 'test'),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.s12),
                      Expanded(
                        child: AppTextField(
                          controller: _maxMarks,
                          label: s.maxMarks,
                          hint: s.maxMarks,
                          keyboardType: TextInputType.number,
                          validator: (v) {
                            final d = double.tryParse(v ?? '');
                            if (d == null || d < 0) return 'Invalid marks';
                            return null;
                          },
                        ),
                      ),
                      const SizedBox(width: AppSpacing.s12),
                      Expanded(
                        child: AppTextField(
                          controller: _weight,
                          label: s.weight,
                          hint: s.weight,
                          keyboardType: TextInputType.number,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.s12),
                  AppTextField(
                    controller: _dueDate,
                    label: s.dueDate,
                    hint: s.dueDateHint,
                    keyboardType: TextInputType.text,
                  ),
                  const SizedBox(height: AppSpacing.s16),
                  Text(s.components, style: context.texts.titleMedium),
                  const SizedBox(height: AppSpacing.s8),
                  ..._components.asMap().entries.map((entry) {
                    final i = entry.key;
                    final c = entry.value;
                    return _buildComponentRow(c, i);
                  }).toList(),
                  AppButton(
                    label: s.addComponent,
                    variant: AppButtonVariant.outline,
                    icon: Icons.add,
                    onPressed: _addComponent,
                  ),
                  const SizedBox(height: AppSpacing.s16),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
