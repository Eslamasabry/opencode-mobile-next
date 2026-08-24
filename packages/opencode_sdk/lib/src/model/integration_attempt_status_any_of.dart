//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf {
  /// Returns a new [IntegrationAttemptStatusAnyOf] instance.
  IntegrationAttemptStatusAnyOf({required this.status, required this.time});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        IntegrationAttemptStatusAnyOfStatusEnum.unknownDefaultOpenApi,
  )
  final IntegrationAttemptStatusAnyOfStatusEnum status;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final IntegrationAttemptStatusAnyOfTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf &&
            runtimeType == other.runtimeType &&
            equals([status, time], [other.status, other.time]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([status, time]);

  factory IntegrationAttemptStatusAnyOf.fromJson(Map<String, dynamic> json) =>
      _$IntegrationAttemptStatusAnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationAttemptStatusAnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationAttemptStatusAnyOfStatusEnum {
  @JsonValue(r'pending')
  pending(r'pending'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationAttemptStatusAnyOfStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
