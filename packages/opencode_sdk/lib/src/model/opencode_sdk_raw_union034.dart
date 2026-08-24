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
class OpencodeSdkRawUnion034 implements OpenCodeRawJsonValue {
  OpencodeSdkRawUnion034(Object? value) : value = _copyJsonValue(value);

  factory OpencodeSdkRawUnion034.fromJson(Object? json) =>
      OpencodeSdkRawUnion034(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"type\":\"string\",\"enum\":[\"session.list\",\"session.new\",\"session.share\",\"session.interrupt\",\"session.compact\",\"session.page.up\",\"session.page.down\",\"session.line.up\",\"session.line.down\",\"session.half.page.up\",\"session.half.page.down\",\"session.first\",\"session.last\",\"prompt.clear\",\"prompt.submit\",\"agent.cycle\"]},{\"type\":\"string\"}]}";

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
