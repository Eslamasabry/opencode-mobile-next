//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_moved_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_moved.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextMoved {
  /// Returns a new [EventSessionNextMoved] instance.
  EventSessionNextMoved({
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
    unknownEnumValue: EventSessionNextMovedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextMovedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextMovedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextMoved &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextMoved.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextMovedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextMovedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextMovedTypeEnum {
  @JsonValue(r'session.next.moved')
  sessionPeriodNextPeriodMoved(r'session.next.moved'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextMovedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
