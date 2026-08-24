//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_integration_attempt_complete_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2IntegrationAttemptCompleteRequest {
  /// Returns a new [V2IntegrationAttemptCompleteRequest] instance.
  V2IntegrationAttemptCompleteRequest({this.code});

  @JsonKey(name: r'code', required: false, includeIfNull: false)
  final String? code;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2IntegrationAttemptCompleteRequest &&
            runtimeType == other.runtimeType &&
            equals([code], [other.code]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([code]);

  factory V2IntegrationAttemptCompleteRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$V2IntegrationAttemptCompleteRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2IntegrationAttemptCompleteRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
