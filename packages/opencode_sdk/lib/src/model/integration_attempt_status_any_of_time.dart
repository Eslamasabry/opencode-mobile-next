//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union025.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union026.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOfTime {
  /// Returns a new [IntegrationAttemptStatusAnyOfTime] instance.
  IntegrationAttemptStatusAnyOfTime({
    required this.created,

    required this.expires,
  });

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion025 created;

  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion026 expires;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOfTime &&
            runtimeType == other.runtimeType &&
            equals([created, expires], [other.created, other.expires]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, expires]);

  factory IntegrationAttemptStatusAnyOfTime.fromJson(
    Map<String, dynamic> json,
  ) => _$IntegrationAttemptStatusAnyOfTimeFromJson(json);

  Map<String, dynamic> toJson() =>
      _$IntegrationAttemptStatusAnyOfTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
