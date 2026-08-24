//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_summarize_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionSummarizeRequest {
  /// Returns a new [SessionSummarizeRequest] instance.
  SessionSummarizeRequest({
    required this.providerID,

    required this.modelID,

    this.auto,
  });

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'modelID', required: true, includeIfNull: false)
  final String modelID;

  @JsonKey(name: r'auto', required: false, includeIfNull: false)
  final bool? auto;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionSummarizeRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [providerID, modelID, auto],
              [other.providerID, other.modelID, other.auto],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([providerID, modelID, auto]);

  factory SessionSummarizeRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionSummarizeRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionSummarizeRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
