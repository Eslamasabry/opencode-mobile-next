//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_switch_agent_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionSwitchAgentRequest {
  /// Returns a new [V2SessionSwitchAgentRequest] instance.
  V2SessionSwitchAgentRequest({required this.agent});

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionSwitchAgentRequest &&
            runtimeType == other.runtimeType &&
            equals([agent], [other.agent]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([agent]);

  factory V2SessionSwitchAgentRequest.fromJson(Map<String, dynamic> json) =>
      _$V2SessionSwitchAgentRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionSwitchAgentRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
