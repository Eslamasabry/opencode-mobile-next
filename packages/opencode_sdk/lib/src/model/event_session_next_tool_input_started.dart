//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_started_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_tool_input_started.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextToolInputStarted {
  /// Returns a new [EventSessionNextToolInputStarted] instance.
  EventSessionNextToolInputStarted({
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
        EventSessionNextToolInputStartedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextToolInputStartedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextToolInputStartedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextToolInputStarted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextToolInputStarted.fromJson(
    Map<String, dynamic> json,
  ) => _$EventSessionNextToolInputStartedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventSessionNextToolInputStartedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextToolInputStartedTypeEnum {
  @JsonValue(r'session.next.tool.input.started')
  sessionPeriodNextPeriodToolPeriodInputPeriodStarted(
    r'session.next.tool.input.started',
  ),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextToolInputStartedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
