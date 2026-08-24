//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'app_log_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AppLogRequest {
  /// Returns a new [AppLogRequest] instance.
  AppLogRequest({
    required this.service,

    required this.level,

    required this.message,

    this.extra,
  });

  /// Service name for the log entry
  @JsonKey(name: r'service', required: true, includeIfNull: false)
  final String service;

  /// Log level
  @JsonKey(
    name: r'level',
    required: true,
    includeIfNull: false,
    unknownEnumValue: AppLogRequestLevelEnum.unknownDefaultOpenApi,
  )
  final AppLogRequestLevelEnum level;

  /// Log message
  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'extra', required: false, includeIfNull: false)
  final Object? extra;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AppLogRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [service, level, message, extra],
              [other.service, other.level, other.message, other.extra],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([service, level, message, extra]);

  factory AppLogRequest.fromJson(Map<String, dynamic> json) =>
      _$AppLogRequestFromJson(json);

  Map<String, dynamic> toJson() => _$AppLogRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

/// Log level
enum AppLogRequestLevelEnum {
  /// Log level
  @JsonValue(r'debug')
  debug(r'debug'),

  /// Log level
  @JsonValue(r'info')
  info(r'info'),

  /// Log level
  @JsonValue(r'error')
  error(r'error'),

  /// Log level
  @JsonValue(r'warn')
  warn(r'warn'),

  /// Log level
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AppLogRequestLevelEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
