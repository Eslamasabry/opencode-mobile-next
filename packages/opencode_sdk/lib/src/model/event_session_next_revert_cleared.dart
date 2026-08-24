//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_cleared_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_revert_cleared.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextRevertCleared {
  /// Returns a new [EventSessionNextRevertCleared] instance.
  EventSessionNextRevertCleared({
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
        EventSessionNextRevertClearedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextRevertClearedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextRevertClearedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextRevertCleared &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextRevertCleared.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextRevertClearedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextRevertClearedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextRevertClearedTypeEnum {
  @JsonValue(r'session.next.revert.cleared')
  sessionPeriodNextPeriodRevertPeriodCleared(r'session.next.revert.cleared'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextRevertClearedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
