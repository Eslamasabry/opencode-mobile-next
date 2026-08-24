//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_agent_switched_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_agent_switched.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextAgentSwitched {
  /// Returns a new [EventSessionNextAgentSwitched] instance.
  EventSessionNextAgentSwitched({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        EventSessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextAgentSwitchedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextAgentSwitchedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextAgentSwitched &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextAgentSwitched.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextAgentSwitchedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextAgentSwitchedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextAgentSwitchedTypeEnum {
  @JsonValue(r'session.next.agent.switched')
  sessionPeriodNextPeriodAgentPeriodSwitched(r'session.next.agent.switched'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextAgentSwitchedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
