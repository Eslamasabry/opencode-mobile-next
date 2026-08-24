//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_started_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_reasoning_started.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextReasoningStarted {
  /// Returns a new [EventSessionNextReasoningStarted] instance.
  EventSessionNextReasoningStarted({
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
        EventSessionNextReasoningStartedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextReasoningStartedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextReasoningStartedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextReasoningStarted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextReasoningStarted.fromJson(
    Map<String, dynamic> json,
  ) => _$EventSessionNextReasoningStartedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventSessionNextReasoningStartedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextReasoningStartedTypeEnum {
  @JsonValue(r'session.next.reasoning.started')
  sessionPeriodNextPeriodReasoningPeriodStarted(
    r'session.next.reasoning.started',
  ),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextReasoningStartedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
