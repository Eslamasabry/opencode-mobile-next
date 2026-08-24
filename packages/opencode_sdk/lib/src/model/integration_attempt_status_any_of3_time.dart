//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union031.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union032.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of3_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf3Time {
  /// Returns a new [IntegrationAttemptStatusAnyOf3Time] instance.
  IntegrationAttemptStatusAnyOf3Time({
    required this.created,

    required this.expires,
  });

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion031 created;

  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion032 expires;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf3Time &&
            runtimeType == other.runtimeType &&
            equals([created, expires], [other.created, other.expires]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, expires]);

  factory IntegrationAttemptStatusAnyOf3Time.fromJson(
    Map<String, dynamic> json,
  ) => _$IntegrationAttemptStatusAnyOf3TimeFromJson(json);

  Map<String, dynamic> toJson() =>
      _$IntegrationAttemptStatusAnyOf3TimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
