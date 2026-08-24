//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_step_failed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_step_failed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextStepFailed {
  /// Returns a new [EventSessionNextStepFailed] instance.
  EventSessionNextStepFailed({
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
    unknownEnumValue: EventSessionNextStepFailedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextStepFailedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextStepFailedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextStepFailed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextStepFailed.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextStepFailedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextStepFailedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextStepFailedTypeEnum {
  @JsonValue(r'session.next.step.failed')
  sessionPeriodNextPeriodStepPeriodFailed(r'session.next.step.failed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextStepFailedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
