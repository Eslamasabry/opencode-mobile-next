//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union025.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union024.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttemptTime {
  /// Returns a new [IntegrationAttemptTime] instance.
  IntegrationAttemptTime({required this.created, required this.expires});

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion024 created;

  @JsonKey(name: r'expires', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion025 expires;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttemptTime &&
            runtimeType == other.runtimeType &&
            equals([created, expires], [other.created, other.expires]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, expires]);

  factory IntegrationAttemptTime.fromJson(Map<String, dynamic> json) =>
      _$IntegrationAttemptTimeFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationAttemptTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
