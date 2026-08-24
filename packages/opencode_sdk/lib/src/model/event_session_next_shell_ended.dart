//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_ended_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_shell_ended.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextShellEnded {
  /// Returns a new [EventSessionNextShellEnded] instance.
  EventSessionNextShellEnded({
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
    unknownEnumValue: EventSessionNextShellEndedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextShellEndedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextShellEndedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextShellEnded &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextShellEnded.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextShellEndedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextShellEndedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextShellEndedTypeEnum {
  @JsonValue(r'session.next.shell.ended')
  sessionPeriodNextPeriodShellPeriodEnded(r'session.next.shell.ended'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextShellEndedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
