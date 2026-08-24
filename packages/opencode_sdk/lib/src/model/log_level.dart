//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';

/// Log level
enum LogLevel {
  /// Log level
  @JsonValue(r'DEBUG')
  DEBUG(r'DEBUG'),

  /// Log level
  @JsonValue(r'INFO')
  INFO(r'INFO'),

  /// Log level
  @JsonValue(r'WARN')
  WARN(r'WARN'),

  /// Log level
  @JsonValue(r'ERROR')
  ERROR(r'ERROR'),

  /// Log level
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const LogLevel(this.value);

  final String value;

  @override
  String toString() => value;
}
