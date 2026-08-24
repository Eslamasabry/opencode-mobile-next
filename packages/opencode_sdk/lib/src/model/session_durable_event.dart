//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

import 'dart:convert';

import 'package:opencode_sdk/src/http/wire.dart';

/// Lossless JSON representation of an OpenAPI union.
///
/// dart-dio flattens anyOf/oneOf branches into an aggregate model, which can
/// reject valid branches or discard branch-specific fields. This wrapper keeps
/// the wire value intact while exposing the exact normalized schema descriptor.
class SessionDurableEvent implements OpenCodeRawJsonValue {
  SessionDurableEvent(Object? value) : value = _copyJsonValue(value);

  factory SessionDurableEvent.fromJson(Object? json) =>
      SessionDurableEvent(json);

  static const String openApiSchemaJson =
      "{\"oneOf\":[{\"\$ref\":\"#/components/schemas/SessionNextAgentSwitched\"},{\"\$ref\":\"#/components/schemas/SessionNextModelSwitched\"},{\"\$ref\":\"#/components/schemas/SessionNextMoved\"},{\"\$ref\":\"#/components/schemas/SessionNextPrompted\"},{\"\$ref\":\"#/components/schemas/SessionNextPromptAdmitted\"},{\"\$ref\":\"#/components/schemas/SessionNextContextUpdated\"},{\"\$ref\":\"#/components/schemas/SessionNextSynthetic\"},{\"\$ref\":\"#/components/schemas/SessionNextShellStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextShellEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextStepStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextStepEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextStepFailed\"},{\"\$ref\":\"#/components/schemas/SessionNextTextStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextTextEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextToolInputStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextToolInputEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextToolCalled\"},{\"\$ref\":\"#/components/schemas/SessionNextToolProgress\"},{\"\$ref\":\"#/components/schemas/SessionNextToolSuccess\"},{\"\$ref\":\"#/components/schemas/SessionNextToolFailed\"},{\"\$ref\":\"#/components/schemas/SessionNextReasoningStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextReasoningEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextRetried\"},{\"\$ref\":\"#/components/schemas/SessionNextCompactionStarted\"},{\"\$ref\":\"#/components/schemas/SessionNextCompactionEnded\"},{\"\$ref\":\"#/components/schemas/SessionNextRevertStaged\"},{\"\$ref\":\"#/components/schemas/SessionNextRevertCleared\"},{\"\$ref\":\"#/components/schemas/SessionNextRevertCommitted\"}]}";

  @override
  final Object? value;

  Object? toJson() => _copyJsonValue(value);

  Map<String, dynamic>? get objectValue =>
      value is Map<String, dynamic> ? value as Map<String, dynamic> : null;

  @override
  String toString() => jsonEncode(value);
}

Object? _copyJsonValue(Object? value) {
  if (value == null || value is String || value is num || value is bool) {
    return value;
  }
  if (value is List) {
    return value.map(_copyJsonValue).toList(growable: false);
  }
  if (value is Map) {
    return value.map<String, dynamic>((key, item) {
      if (key is! String) {
        throw ArgumentError.value(
          value,
          'value',
          'JSON object keys must be strings',
        );
      }
      return MapEntry(key, _copyJsonValue(item));
    });
  }
  throw ArgumentError.value(value, 'value', 'Not a JSON value');
}
