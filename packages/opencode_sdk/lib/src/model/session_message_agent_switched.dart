//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_agent_switched.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAgentSwitched {
  /// Returns a new [SessionMessageAgentSwitched] instance.
  SessionMessageAgentSwitched({
    required this.id,

    this.metadata,

    required this.time,

    required this.type,

    required this.agent,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageAgentSwitchedTime time;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageAgentSwitchedTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageAgentSwitchedTypeEnum type;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAgentSwitched &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, time, type, agent],
              [other.id, other.metadata, other.time, other.type, other.agent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, time, type, agent]);

  factory SessionMessageAgentSwitched.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAgentSwitchedFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageAgentSwitchedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageAgentSwitchedTypeEnum {
  @JsonValue(r'agent-switched')
  agentSwitched(r'agent-switched'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageAgentSwitchedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
