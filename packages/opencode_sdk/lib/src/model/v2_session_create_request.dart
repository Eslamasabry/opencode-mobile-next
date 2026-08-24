//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_create_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionCreateRequest {
  /// Returns a new [V2SessionCreateRequest] instance.
  V2SessionCreateRequest({this.id, this.agent, this.model, this.location});

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final ModelRef? model;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionCreateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, agent, model, location],
              [other.id, other.agent, other.model, other.location],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, agent, model, location]);

  factory V2SessionCreateRequest.fromJson(Map<String, dynamic> json) =>
      _$V2SessionCreateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionCreateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
