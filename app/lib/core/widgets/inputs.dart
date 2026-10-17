import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/strings.dart';
import '../theme/tokens.dart';

enum AppButtonVariant { primary, secondary, outline, text, destructive }

/// Standard action button (specification section 7.1). One place decides shape,
/// size, and touch target; screens never style buttons individually.
class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    this.onPressed,
    this.variant = AppButtonVariant.primary,
    this.icon,
    this.iconOnly = false,
    this.tooltip,
    this.expand = false,
    this.busy = false,
  });

  const AppButton.icon({
    super.key,
    required this.label,
    this.onPressed,
    required this.icon,
    this.variant = AppButtonVariant.secondary,
    this.tooltip,
    this.busy = false,
  })  : iconOnly = true,
        expand = false;

  final String label;
  final VoidCallback? onPressed;
  final AppButtonVariant variant;
  final IconData? icon;
  final bool iconOnly;
  final String? tooltip;
  final bool expand;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final child = busy
        ? const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : iconOnly
            ? Icon(icon ?? Icons.circle)
            : Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (icon != null) ...[
                    Icon(icon, size: 18),
                    const SizedBox(width: AppSpacing.s8),
                  ],
                  Flexible(child: Text(label, overflow: TextOverflow.ellipsis)),
                ],
              );

    final effectiveOnPressed = busy ? null : onPressed;
    Widget button = switch (variant) {
      AppButtonVariant.primary => FilledButton(onPressed: effectiveOnPressed, child: child),
      AppButtonVariant.secondary => FilledButton.tonal(onPressed: effectiveOnPressed, child: child),
      AppButtonVariant.outline => OutlinedButton(onPressed: effectiveOnPressed, child: child),
      AppButtonVariant.text => TextButton(onPressed: effectiveOnPressed, child: child),
      AppButtonVariant.destructive => FilledButton(
          onPressed: effectiveOnPressed,
          style: FilledButton.styleFrom(
            backgroundColor: context.scheme.error,
            foregroundColor: context.scheme.onError,
          ),
          child: child,
        ),
    };

    if (iconOnly && tooltip != null) {
      button = Tooltip(message: tooltip!, child: button);
    }
    if (expand) {
      button = SizedBox(width: double.infinity, child: button);
    }
    // Every icon-only button must carry a semantic label (section 4.4).
    return Semantics(
      label: iconOnly ? (label.isEmpty ? tooltip : label) : null,
      button: true,
      child: button,
    );
  }
}

/// Validated text input with a persistent label, helper text, and required
/// indicator (specification section 8.2).
class AppTextField extends StatelessWidget {
  const AppTextField({
    super.key,
    this.controller,
    required this.label,
    this.helper,
    this.hint,
    this.obscure = false,
    this.required = false,
    this.enabled = true,
    this.validator,
    this.keyboardType,
    this.autofillHints,
    this.prefixIcon,
    this.suffix,
    this.maxLines = 1,
    this.inputFormatters,
    this.onSubmitted,
    this.onChanged,
    this.textCapitalization = TextCapitalization.none,
    this.autofocus = false,
    this.focusNode,
  });

  final TextEditingController? controller;
  final String label;
  final String? helper;
  final String? hint;
  final bool obscure;
  final bool required;
  final bool enabled;
  final String? Function(String? value)? validator;
  final TextInputType? keyboardType;
  final Iterable<String>? autofillHints;
  final IconData? prefixIcon;
  final Widget? suffix;
  final int maxLines;
  final List<TextInputFormatter>? inputFormatters;
  final ValueChanged<String>? onSubmitted;
  final ValueChanged<String>? onChanged;
  final TextCapitalization textCapitalization;
  final bool autofocus;
  final FocusNode? focusNode;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      obscureText: obscure,
      enabled: enabled,
      validator: validator,
      keyboardType: keyboardType,
      autofillHints: autofillHints,
      maxLines: obscure ? 1 : maxLines,
      inputFormatters: inputFormatters,
      onFieldSubmitted: onSubmitted,
      onChanged: onChanged,
      textCapitalization: textCapitalization,
      autofocus: autofocus,
      focusNode: focusNode,
      decoration: InputDecoration(
        label: Text(required ? '$label *' : label),
        helperText: helper,
        hintText: hint,
        prefixIcon: prefixIcon == null ? null : Icon(prefixIcon),
        suffixIcon: suffix,
      ),
    );
  }
}

/// Validated single-select field.
class AppSelectField<T> extends StatelessWidget {
  const AppSelectField({
    super.key,
    required this.label,
    required this.value,
    required this.items,
    required this.itemLabel,
    this.onChanged,
    this.required = false,
    this.helper,
    this.enabled = true,
    this.hint,
  });

  final String label;
  final T? value;
  final List<T> items;
  final String Function(T item) itemLabel;
  final ValueChanged<T?>? onChanged;
  final bool required;
  final String? helper;
  final bool enabled;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    return InputDecorator(
      decoration: InputDecoration(
        label: Text(required ? '$label *' : label),
        helperText: helper,
        enabled: enabled,
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<T>(
          isDense: true,
          isExpanded: true,
          value: value,
          hint: hint == null ? null : Text(hint!),
          onChanged: enabled ? onChanged : null,
          items: [
            for (final item in items)
              DropdownMenuItem<T>(value: item, child: Text(itemLabel(item), overflow: TextOverflow.ellipsis)),
          ],
        ),
      ),
    );
  }
}

/// Date-range field backed by the platform range picker.
class DateRangeField extends StatelessWidget {
  const DateRangeField({
    super.key,
    required this.label,
    required this.range,
    required this.onChanged,
    this.enabled = true,
    this.format,
  });

  final String label;
  final DateTimeRange? range;
  final ValueChanged<DateTimeRange?> onChanged;
  final bool enabled;
  final String Function(DateTime date)? format;

  @override
  Widget build(BuildContext context) {
    final f = format ?? (d) => '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
    final s = S.of(context);
    final text = range == null ? s.selectDates : '${f(range!.start)} – ${f(range!.end)}';
    return InkWell(
      borderRadius: AppRadius.mdAll,
      onTap: enabled
          ? () async {
              final picked = await showDateRangePicker(
                context: context,
                firstDate: DateTime(2000),
                lastDate: DateTime(2100),
                initialDateRange: range,
              );
              if (picked != null) onChanged(picked);
            }
          : null,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          enabled: enabled,
          suffixIcon: const Icon(Icons.date_range_outlined),
        ),
        child: Text(text, style: context.texts.bodyMedium),
      ),
    );
  }
}

/// Upload field with type/size validation, progress, and retry
/// (specification section 7.1). Actual file picking is provided by the host
/// platform through [onPick].
class FileUploadField extends StatelessWidget {
  const FileUploadField({
    super.key,
    required this.label,
    this.helper,
    this.progress,
    this.errorText,
    this.fileName,
    this.allowedExtensions = const ['pdf', 'png', 'jpg', 'jpeg', 'docx', 'xlsx'],
    this.maxBytes = 10 * 1024 * 1024,
    this.onPick,
    this.onRetry,
    this.enabled = true,
  });

  final String label;
  final String? helper;
  final double? progress;
  final String? errorText;
  final String? fileName;
  final List<String> allowedExtensions;
  final int maxBytes;
  final VoidCallback? onPick;
  final VoidCallback? onRetry;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final hint = helper ?? s.uploadHint(allowedExtensions.join(', '), (maxBytes / (1024 * 1024)).round());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: context.texts.labelMedium),
        const SizedBox(height: AppSpacing.s8),
        UploadDropZone(
          child: Column(
            children: [
              Icon(Icons.upload_file_outlined, size: 28, color: context.tokens.textSecondary),
              const SizedBox(height: AppSpacing.s8),
              Text(fileName ?? s.chooseFile, style: context.texts.bodyMedium),
              const SizedBox(height: AppSpacing.s4),
              Text(hint, style: context.texts.bodySmall, textAlign: TextAlign.center),
              if (progress != null) ...[
                const SizedBox(height: AppSpacing.s8),
                LinearProgressIndicator(value: progress),
              ],
              if (errorText != null) ...[
                const SizedBox(height: AppSpacing.s8),
                Text(errorText!, style: context.texts.bodySmall?.copyWith(color: context.scheme.error)),
              ],
              const SizedBox(height: AppSpacing.s8),
              if (onRetry != null)
                AppButton(label: s.retry, variant: AppButtonVariant.text, onPressed: onRetry)
              else
                AppButton(
                  label: fileName == null ? s.chooseFile : s.replaceFile,
                  variant: AppButtonVariant.outline,
                  onPressed: enabled ? onPick : null,
                ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Simple outlined drop area used by [FileUploadField].
class UploadDropZone extends StatelessWidget {
  const UploadDropZone({super.key, required this.child, this.padding});

  final Widget child;
  final EdgeInsets? padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding ?? const EdgeInsets.all(AppSpacing.s16),
      decoration: BoxDecoration(
        borderRadius: AppRadius.mdAll,
        border: Border.all(color: context.tokens.border),
        color: context.tokens.canvas,
      ),
      child: child,
    );
  }
}
