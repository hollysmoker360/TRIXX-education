import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../l10n/strings.dart';
import '../networking/api_problem.dart';
import '../session/session.dart';

final recordsProvider = FutureProvider.autoDispose.family<List<Map<String, dynamic>>, String>((ref, path) async {
  final api = ref.watch(apiClientProvider);
  final rows = <Map<String, dynamic>>[];
  var page = 0;
  while (true) {
    final data = await api.get(path, query: {'page': page, 'size': 100});
    if (data is List) return data.cast<Map<String, dynamic>>();
    final result = data as Map<String, dynamic>;
    final items = (result['items'] as List).cast<Map<String, dynamic>>();
    rows.addAll(items);
    if (items.isEmpty || rows.length >= (result['total'] as num)) return rows;
    page++;
  }
});

Map<String, String> recordOptions(List<Map<String, dynamic>> rows, {String id = 'id'}) => {
  for (final row in rows) '${row[id]}': '${row['name'] ?? row['displayName'] ?? '${row['firstName']} ${row['lastName']}'}',
};

enum FieldKind { text, multiline, integer, date, time, email, url, toggle }

class RecordField {
  const RecordField(this.name, this.label, {this.kind = FieldKind.text, this.required = true,
    this.options, this.minimum = 1, this.maximum});
  final String name;
  final String label;
  final FieldKind kind;
  final bool required;
  final Map<String, String>? options;
  final int minimum;
  final int? maximum;
}

String? validateRecordField(RecordField field, String value) {
  if (value.isEmpty) return field.required ? '${field.label} is required.' : null;
  switch (field.kind) {
    case FieldKind.integer:
      final number = int.tryParse(value);
      if (number == null || number < field.minimum || (field.maximum != null && number > field.maximum!)) {
        return 'Enter a whole number from ${field.minimum}${field.maximum == null ? '' : ' to ${field.maximum}'}.';
      }
    case FieldKind.date:
      final date = DateTime.tryParse(value);
      if (!RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value) || date == null || date.toIso8601String().substring(0, 10) != value) {
        return 'Use a valid date: YYYY-MM-DD.';
      }
    case FieldKind.time:
      if (!RegExp(r'^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$').hasMatch(value)) return 'Use 24-hour time: HH:mm.';
    case FieldKind.email:
      if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(value)) return 'Enter a valid email address.';
    case FieldKind.url:
      final uri = Uri.tryParse(value);
      if (uri == null || !['http', 'https'].contains(uri.scheme) || uri.host.isEmpty) return 'Enter an HTTP or HTTPS URL.';
    default:
      break;
  }
  return null;
}

Future<bool> showRecordEditor(BuildContext context, {required String title,
  required List<RecordField> fields, Map<String, dynamic> initial = const {},
  required Future<void> Function(Map<String, dynamic>) onSave}) async =>
  await showDialog<bool>(context: context, barrierDismissible: false,
    builder: (_) => _RecordEditor(title: title, fields: fields, initial: initial, onSave: onSave)) ?? false;

class _RecordEditor extends StatefulWidget {
  const _RecordEditor({required this.title, required this.fields, required this.initial, required this.onSave});
  final String title;
  final List<RecordField> fields;
  final Map<String, dynamic> initial;
  final Future<void> Function(Map<String, dynamic>) onSave;
  @override
  State<_RecordEditor> createState() => _RecordEditorState();
}

class _RecordEditorState extends State<_RecordEditor> {
  final _form = GlobalKey<FormState>();
  late final _values = Map<String, dynamic>.from(widget.initial);
  bool _saving = false;
  String? _error;

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    _form.currentState!.save();
    final start = _values['startsOn'] ?? _values['startsAt'];
    final end = _values['endsOn'] ?? _values['endsAt'];
    if (start is String && end is String && start.compareTo(end) >= 0) {
      setState(() => _error = 'The end must be after the start.');
      return;
    }
    setState(() { _saving = true; _error = null; });
    try {
      await widget.onSave({for (final field in widget.fields) field.name: _values[field.name]});
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() { _saving = false; _error = e is ApiProblem ? e.detail : 'Could not save. Please try again.'; });
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !_saving,
    child: AlertDialog(
      title: Text(widget.title),
      scrollable: true,
      content: SizedBox(width: 480, child: Form(key: _form,
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          for (final field in widget.fields) Padding(padding: const EdgeInsets.only(bottom: 16), child: _field(field)),
          if (_error != null) Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          if (_saving) const LinearProgressIndicator(),
        ]))),
      actions: [
        TextButton(onPressed: _saving ? null : () => Navigator.pop(context, false), child: Text(S.of(context).cancel)),
        FilledButton(onPressed: _saving ? null : _save, child: Text(_saving ? 'Saving…' : S.of(context).save)),
      ],
    ),
  );

  Widget _field(RecordField field) {
    final initial = _values[field.name];
    if (field.kind == FieldKind.toggle) {
      return SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(field.label),
        value: initial == true, onChanged: _saving ? null : (value) => setState(() => _values[field.name] = value));
    }
    if (field.options != null) {
      return DropdownButtonFormField<String>(
        initialValue: field.options!.containsKey('$initial') ? '$initial' : null,
        isExpanded: true,
        decoration: InputDecoration(labelText: field.label, helperText: field.options!.isEmpty ? S.of(context).noOptionsAvailable : null),
        items: [if (!field.required) DropdownMenuItem(value: '', child: Text(S.of(context).notSet)),
          for (final option in field.options!.entries) DropdownMenuItem(value: option.key, child: Text(option.value, overflow: TextOverflow.ellipsis))],
        onChanged: _saving ? null : (value) => _values[field.name] = value == '' ? null : value,
        validator: (value) => field.required && (value == null || value.isEmpty) ? S.of(context).fieldRequired(field.label) : null,
      );
    }
    return TextFormField(
      initialValue: initial == null ? '' : '$initial',
      enabled: !_saving,
      decoration: InputDecoration(labelText: field.label,
        hintText: field.kind == FieldKind.date ? 'YYYY-MM-DD' : field.kind == FieldKind.time ? 'HH:mm' : null),
      keyboardType: switch (field.kind) { FieldKind.integer => TextInputType.number, FieldKind.email => TextInputType.emailAddress,
        FieldKind.url => TextInputType.url, FieldKind.multiline => TextInputType.multiline, _ => TextInputType.text },
      minLines: field.kind == FieldKind.multiline ? 3 : 1,
      maxLines: field.kind == FieldKind.multiline ? 6 : 1,
      validator: (value) => validateRecordField(field, value?.trim() ?? ''),
      onSaved: (value) {
        final text = value?.trim() ?? '';
        _values[field.name] = text.isEmpty ? null : field.kind == FieldKind.integer ? int.parse(text) : text;
      },
    );
  }
}

Future<bool> confirmAction(BuildContext context, String title, String message) async =>
  await showDialog<bool>(context: context, builder: (context) => AlertDialog(
    title: Text(title), content: Text(message), actions: [
      TextButton(onPressed: () => Navigator.pop(context, false), child: Text(S.of(context).cancel)),
      FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(S.of(context).confirm)),
    ],
  )) ?? false;
