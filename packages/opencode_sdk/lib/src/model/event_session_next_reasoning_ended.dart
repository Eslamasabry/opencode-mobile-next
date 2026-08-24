//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_ended_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_reasoning_ended.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextReasoningEnded {
  /// Returns a new [EventSessionNextReasoningEnded] instance.
  EventSessionNextReasoningEnded({
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
        EventSessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextReasoningEndedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextReasoningEndedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextReasoningEnded &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextReasoningEnded.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextReasoningEndedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextReasoningEndedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextReasoningEndedTypeEnum {
  @JsonValue(r'session.next.reasoning.ended')
  sessionPeriodNextPeriodReasoningPeriodEnded(r'session.next.reasoning.ended'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextReasoningEndedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
