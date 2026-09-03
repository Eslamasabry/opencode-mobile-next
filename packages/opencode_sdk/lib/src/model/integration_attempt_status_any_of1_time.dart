//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union028.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union029.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of1_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf1Time {
  /// Returns a new [IntegrationAttemptStatusAnyOf1Time] instance.
  IntegrationAttemptStatusAnyOf1Time({
    required this.created,

    required this.expires,
  });

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion028 created;

  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion029 expires;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf1Time &&
            runtimeType == other.runtimeType &&
            equals([created, expires], [other.created, other.expires]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, expires]);

  factory IntegrationAttemptStatusAnyOf1Time.fromJson(
    Map<String, dynamic> json,
  ) => _$IntegrationAttemptStatusAnyOf1TimeFromJson(json);

  Map<String, dynamic> toJson() =>
      _$IntegrationAttemptStatusAnyOf1TimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
