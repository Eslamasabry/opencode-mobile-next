import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../api2/models.dart';

export '../api2/models.dart'
    show
        Api2FormInfo,
        Api2FormField,
        Api2FormFieldType,
        Api2FormCondition,
        Api2FormOption;

class FormRequestIdentity {
  const FormRequestIdentity({
    required this.profileID,
    required this.directory,
    required this.sessionID,
    required this.formID,
    required this.revision,
    this.workspace,
  });
  final String profileID;
  final String directory;
  final String sessionID;
  final String formID;
  final String revision;
  final String? workspace;

  /// Includes the revision so stale widgets cannot share draft or reply state.
  String get key =>
      _hash([profileID, directory, workspace, sessionID, formID, revision]);
}

class CapturedFormRequest {
  const CapturedFormRequest({
    required this.form,
    required this.identity,
    required this.isPending,
    required this.reply,
    required this.cancel,
  });
  final Api2FormInfo form;
  final FormRequestIdentity identity;
  final bool Function() isPending;
  final Future<void> Function(Map<String, dynamic>) reply;
  final Future<void> Function() cancel;
}

/// Hash every field that can affect rendering, validation, or interpretation.
/// Map insertion order is not schema identity; list order and JSON types are.
String formSchemaRevision(Api2FormInfo form) => _hash({
  'id': form.id,
  'sessionID': form.sessionID,
  'title': form.title,
  'metadata': form.metadata,
  'fields': [for (final field in form.fields) _fieldJson(field)],
});

/// A captured request owns its schema even when the source mutates or refreshes.
Api2FormInfo snapshotForm(Api2FormInfo form) => Api2FormInfo(
  id: form.id,
  sessionID: form.sessionID,
  title: form.title,
  metadata: form.metadata == null
      ? null
      : _freeze(form.metadata) as Map<String, dynamic>,
  fields: List.unmodifiable([
    for (final field in form.fields)
      Api2FormField(
        key: field.key,
        type: field.type,
        title: field.title,
        description: field.description,
        required: field.required,
        when: List.unmodifiable([
          for (final condition in field.when)
            Api2FormCondition(
              key: condition.key,
              op: condition.op,
              value: _freeze(condition.value),
            ),
        ]),
        format: field.format,
        minLength: field.minLength,
        maxLength: field.maxLength,
        pattern: field.pattern,
        placeholder: field.placeholder,
        defaultValue: _freeze(field.defaultValue),
        options: List.unmodifiable([
          for (final option in field.options)
            Api2FormOption(
              value: option.value,
              label: option.label,
              description: option.description,
            ),
        ]),
        custom: field.custom,
        minimum: field.minimum,
        maximum: field.maximum,
        minItems: field.minItems,
        maxItems: field.maxItems,
        url: field.url,
      ),
  ]),
);

Map<String, dynamic> _fieldJson(Api2FormField field) => {
  'key': field.key,
  'type': field.type.name,
  'title': field.title,
  'description': field.description,
  'required': field.required,
  'when': [
    for (final condition in field.when)
      {'key': condition.key, 'op': condition.op, 'value': condition.value},
  ],
  'format': field.format,
  'minLength': field.minLength,
  'maxLength': field.maxLength,
  'pattern': field.pattern,
  'placeholder': field.placeholder,
  'default': field.defaultValue,
  'options': [
    for (final option in field.options)
      {
        'value': option.value,
        'label': option.label,
        'description': option.description,
      },
  ],
  'custom': field.custom,
  'minimum': field.minimum,
  'maximum': field.maximum,
  'minItems': field.minItems,
  'maxItems': field.maxItems,
  'url': field.url,
};

Object? _freeze(Object? value) {
  if (value is Map) {
    return Map<String, dynamic>.unmodifiable({
      for (final entry in value.entries)
        entry.key as String: _freeze(entry.value),
    });
  }
  if (value is List) return List<dynamic>.unmodifiable(value.map(_freeze));
  return value;
}

Object? _canonical(Object? value) {
  if (value is Map) {
    final keys = value.keys.cast<String>().toList()..sort();
    return {for (final key in keys) key: _canonical(value[key])};
  }
  if (value is List) return value.map(_canonical).toList();
  return value;
}

String _hash(Object? value) =>
    sha256.convert(utf8.encode(jsonEncode(_canonical(value)))).toString();
