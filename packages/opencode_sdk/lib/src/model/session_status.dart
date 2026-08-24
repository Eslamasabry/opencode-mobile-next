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
class SessionStatus implements OpenCodeRawJsonValue {
  SessionStatus(Object? value) : value = _copyJsonValue(value);

  factory SessionStatus.fromJson(Object? json) => SessionStatus(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\",\"enum\":[\"idle\"]}},\"required\":[\"type\"],\"additionalProperties\":false},{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\",\"enum\":[\"retry\"]},\"attempt\":{\"type\":\"integer\",\"minimum\":0},\"message\":{\"type\":\"string\"},\"action\":{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\"},\"provider\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"},\"message\":{\"type\":\"string\"},\"label\":{\"type\":\"string\"},\"link\":{\"type\":\"string\"}},\"required\":[\"reason\",\"provider\",\"title\",\"message\",\"label\"],\"additionalProperties\":false},\"next\":{\"type\":\"integer\",\"minimum\":0}},\"required\":[\"type\",\"attempt\",\"message\",\"next\"],\"additionalProperties\":false},{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\",\"enum\":[\"busy\"]}},\"required\":[\"type\"],\"additionalProperties\":false}]}";

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
