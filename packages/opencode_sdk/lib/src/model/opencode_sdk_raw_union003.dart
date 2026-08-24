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
class OpencodeSdkRawUnion003 implements OpenCodeRawJsonValue {
  OpencodeSdkRawUnion003(Object? value) : value = _copyJsonValue(value);

  factory OpencodeSdkRawUnion003.fromJson(Object? json) =>
      OpencodeSdkRawUnion003(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"type\":\"string\",\"pattern\":\"^#[0-9a-fA-F]{6}\$\"},{\"type\":\"string\",\"enum\":[\"primary\",\"secondary\",\"accent\",\"success\",\"warning\",\"error\",\"info\"]}],\"description\":\"Hex color code (e.g., #FF5733) or theme color (e.g., primary)\"}";

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
