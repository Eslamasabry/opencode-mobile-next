//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_step_ended_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_step_ended.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextStepEnded {
  /// Returns a new [EventSessionNextStepEnded] instance.
  EventSessionNextStepEnded({
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
    unknownEnumValue: EventSessionNextStepEndedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextStepEndedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextStepEndedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextStepEnded &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextStepEnded.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextStepEndedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextStepEndedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextStepEndedTypeEnum {
  @JsonValue(r'session.next.step.ended')
  sessionPeriodNextPeriodStepPeriodEnded(r'session.next.step.ended'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextStepEndedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
