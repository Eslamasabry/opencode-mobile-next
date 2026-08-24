//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:opencode_sdk/src/model/agent_v2_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_agent_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2AgentList200Response {
  /// Returns a new [V2AgentList200Response] instance.
  V2AgentList200Response({required this.location, required this.data});

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationInfo location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<AgentV2Info> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2AgentList200Response &&
            runtimeType == other.runtimeType &&
            equals([location, data], [other.location, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([location, data]);

  factory V2AgentList200Response.fromJson(Map<String, dynamic> json) =>
      _$V2AgentList200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2AgentList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
