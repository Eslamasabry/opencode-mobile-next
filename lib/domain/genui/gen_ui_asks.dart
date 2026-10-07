sealed class GenUiAsk {
  const GenUiAsk();
}

final class GenUiOption {
  const GenUiOption({required this.id, required this.label, this.detail});
  final String id;
  final String label;
  final String? detail;
}

final class GenUiChoiceAsk extends GenUiAsk {
  GenUiChoiceAsk({required List<GenUiOption> options, this.multi = false})
    : options = List.unmodifiable(options);
  final List<GenUiOption> options;
  final bool multi;
}

enum GenUiFieldType { text, multiline, number, toggle, select, date }

final class GenUiField {
  GenUiField({
    required this.id,
    required this.label,
    required this.type,
    this.required = false,
    this.placeholder,
    this.defaultValue,
    List<GenUiOption> options = const [],
    this.min,
    this.max,
  }) : options = List.unmodifiable(options);
  final String id;
  final String label;
  final GenUiFieldType type;
  final bool required;
  final String? placeholder;

  /// Only String, num, bool, or null (unset); checked at the trust boundary.
  final Object? defaultValue;
  final List<GenUiOption> options;
  final double? min;
  final double? max;
}

final class GenUiFormAsk extends GenUiAsk {
  GenUiFormAsk({required List<GenUiField> fields, this.submitLabel})
    : fields = List.unmodifiable(fields);
  final List<GenUiField> fields;
  final String? submitLabel;
}

enum GenUiConfirmTone { normal, danger }

final class GenUiConfirmAsk extends GenUiAsk {
  const GenUiConfirmAsk({
    this.confirmLabel,
    this.cancelLabel,
    this.tone = GenUiConfirmTone.normal,
  });
  final String? confirmLabel;
  final String? cancelLabel;
  final GenUiConfirmTone tone;
}

final class GenUiPhotoAsk extends GenUiAsk {
  const GenUiPhotoAsk({required this.purpose, this.max = 1});
  final String purpose;
  final int max;
}
