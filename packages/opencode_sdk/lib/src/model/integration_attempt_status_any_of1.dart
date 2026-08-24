//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of1_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf1 {
  /// Returns a new [IntegrationAttemptStatusAnyOf1] instance.
  IntegrationAttemptStatusAnyOf1({required this.status, required this.time});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        IntegrationAttemptStatusAnyOf1StatusEnum.unknownDefaultOpenApi,
  )
  final IntegrationAttemptStatusAnyOf1StatusEnum status;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final IntegrationAttemptStatusAnyOf1Time time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf1 &&
            runtimeType == other.runtimeType &&
            equals([status, time], [other.status, other.time]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([status, time]);

  factory IntegrationAttemptStatusAnyOf1.fromJson(Map<String, dynamic> json) =>
      _$IntegrationAttemptStatusAnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationAttemptStatusAnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationAttemptStatusAnyOf1StatusEnum {
  @JsonValue(r'complete')
  complete(r'complete'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationAttemptStatusAnyOf1StatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
