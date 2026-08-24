//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'service_unavailable_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ServiceUnavailableError {
  /// Returns a new [ServiceUnavailableError] instance.
  ServiceUnavailableError({
    required this.tag,

    required this.message,

    this.service,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ServiceUnavailableErrorTagEnum.unknownDefaultOpenApi,
  )
  final ServiceUnavailableErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'service', required: false, includeIfNull: false)
  final String? service;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ServiceUnavailableError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, message, service],
              [other.tag, other.message, other.service],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, message, service]);

  factory ServiceUnavailableError.fromJson(Map<String, dynamic> json) =>
      _$ServiceUnavailableErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ServiceUnavailableErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ServiceUnavailableErrorTagEnum {
  @JsonValue(r'ServiceUnavailableError')
  serviceUnavailableError(r'ServiceUnavailableError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ServiceUnavailableErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
