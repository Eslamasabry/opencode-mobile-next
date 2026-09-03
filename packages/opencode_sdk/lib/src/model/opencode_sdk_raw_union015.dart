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
class OpencodeSdkRawUnion015 implements OpenCodeRawJsonValue {
  OpencodeSdkRawUnion015(Object? value) : value = _copyJsonValue(value);

  factory OpencodeSdkRawUnion015.fromJson(Object? json) =>
      OpencodeSdkRawUnion015(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"type\":\"boolean\"},{\"type\":\"object\",\"additionalProperties\":{\"anyOf\":[{\"type\":\"object\",\"properties\":{\"disabled\":{\"type\":\"boolean\",\"enum\":[true]}},\"required\":[\"disabled\"],\"additionalProperties\":false},{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"extensions\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"disabled\":{\"type\":\"boolean\"},\"env\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}},\"initialization\":{\"type\":\"object\"}},\"required\":[\"command\"],\"additionalProperties\":false}]}}],\"description\":\"Enable or configure LSP servers. Omit or set to false to disable, true to enable built-ins, or an object to enable built-ins with overrides.\"}";

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
