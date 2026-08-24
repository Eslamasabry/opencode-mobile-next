//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of2_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf2 {
  /// Returns a new [IntegrationAttemptStatusAnyOf2] instance.
  IntegrationAttemptStatusAnyOf2({
    required this.status,

    required this.message,

    required this.time,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        IntegrationAttemptStatusAnyOf2StatusEnum.unknownDefaultOpenApi,
  )
  final IntegrationAttemptStatusAnyOf2StatusEnum status;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final IntegrationAttemptStatusAnyOf2Time time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf2 &&
            runtimeType == other.runtimeType &&
            equals(
              [status, message, time],
              [other.status, other.message, other.time],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([status, message, time]);

  factory IntegrationAttemptStatusAnyOf2.fromJson(Map<String, dynamic> json) =>
      _$IntegrationAttemptStatusAnyOf2FromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationAttemptStatusAnyOf2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationAttemptStatusAnyOf2StatusEnum {
  @JsonValue(r'failed')
  failed(r'failed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationAttemptStatusAnyOf2StatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
