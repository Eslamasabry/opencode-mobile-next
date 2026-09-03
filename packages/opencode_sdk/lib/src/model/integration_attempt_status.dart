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
class IntegrationAttemptStatus implements OpenCodeRawJsonValue {
  IntegrationAttemptStatus(Object? value) : value = _copyJsonValue(value);

  factory IntegrationAttemptStatus.fromJson(Object? json) =>
      IntegrationAttemptStatus(json);

  static const String openApiSchemaJson =
      "{\"anyOf\":[{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"pending\"]},\"time\":{\"type\":\"object\",\"properties\":{\"created\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion026\"},\"expires\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion027\"}},\"required\":[\"created\",\"expires\"],\"additionalProperties\":false}},\"required\":[\"status\",\"time\"],\"additionalProperties\":false},{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"complete\"]},\"time\":{\"type\":\"object\",\"properties\":{\"created\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion028\"},\"expires\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion029\"}},\"required\":[\"created\",\"expires\"],\"additionalProperties\":false}},\"required\":[\"status\",\"time\"],\"additionalProperties\":false},{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"failed\"]},\"message\":{\"type\":\"string\"},\"time\":{\"type\":\"object\",\"properties\":{\"created\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion030\"},\"expires\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion031\"}},\"required\":[\"created\",\"expires\"],\"additionalProperties\":false}},\"required\":[\"status\",\"message\",\"time\"],\"additionalProperties\":false},{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"expired\"]},\"time\":{\"type\":\"object\",\"properties\":{\"created\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion032\"},\"expires\":{\"\$ref\":\"#/components/schemas/OpencodeSdkRawUnion033\"}},\"required\":[\"created\",\"expires\"],\"additionalProperties\":false}},\"required\":[\"status\",\"time\"],\"additionalProperties\":false}]}";

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
