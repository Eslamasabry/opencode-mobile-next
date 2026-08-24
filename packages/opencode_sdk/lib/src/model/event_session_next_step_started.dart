//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_step_started_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_step_started.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextStepStarted {
  /// Returns a new [EventSessionNextStepStarted] instance.
  EventSessionNextStepStarted({
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
    unknownEnumValue: EventSessionNextStepStartedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextStepStartedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextStepStartedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextStepStarted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextStepStarted.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextStepStartedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextStepStartedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextStepStartedTypeEnum {
  @JsonValue(r'session.next.step.started')
  sessionPeriodNextPeriodStepPeriodStarted(r'session.next.step.started'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextStepStartedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
