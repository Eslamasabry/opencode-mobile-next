//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_started_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_shell_started.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextShellStarted {
  /// Returns a new [EventSessionNextShellStarted] instance.
  EventSessionNextShellStarted({
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
        EventSessionNextShellStartedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextShellStartedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextShellStartedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextShellStarted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextShellStarted.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextShellStartedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextShellStartedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextShellStartedTypeEnum {
  @JsonValue(r'session.next.shell.started')
  sessionPeriodNextPeriodShellPeriodStarted(r'session.next.shell.started'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextShellStartedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
