//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union031.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union030.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_status_any_of2_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptStatusAnyOf2Time {
  /// Returns a new [IntegrationAttemptStatusAnyOf2Time] instance.
  IntegrationAttemptStatusAnyOf2Time({
    required this.created,

    required this.expires,
  });

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion030 created;

  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion031 expires;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptStatusAnyOf2Time &&
            runtimeType == other.runtimeType &&
            equals([created, expires], [other.created, other.expires]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, expires]);

  factory IntegrationAttemptStatusAnyOf2Time.fromJson(
    Map<String, dynamic> json,
  ) => _$IntegrationAttemptStatusAnyOf2TimeFromJson(json);

  Map<String, dynamic> toJson() =>
      _$IntegrationAttemptStatusAnyOf2TimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
