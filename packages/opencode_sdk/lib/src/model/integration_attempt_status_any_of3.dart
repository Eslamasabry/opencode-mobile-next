//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of3_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of3.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf3 {
  /// Returns a new [IntegrationAttemptStatusAnyOf3] instance.
  IntegrationAttemptStatusAnyOf3({required this.status, required this.time});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        IntegrationAttemptStatusAnyOf3StatusEnum.unknownDefaultOpenApi,
  )
  final IntegrationAttemptStatusAnyOf3StatusEnum status;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final IntegrationAttemptStatusAnyOf3Time time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf3 &&
            runtimeType == other.runtimeType &&
            equals([status, time], [other.status, other.time]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([status, time]);

  factory IntegrationAttemptStatusAnyOf3.fromJson(Map<String, dynamic> json) =>
      _$IntegrationAttemptStatusAnyOf3FromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationAttemptStatusAnyOf3ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationAttemptStatusAnyOf3StatusEnum {
  @JsonValue(r'expired')
  expired(r'expired'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationAttemptStatusAnyOf3StatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
