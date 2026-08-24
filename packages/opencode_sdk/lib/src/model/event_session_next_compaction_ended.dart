//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_ended_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_compaction_ended.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextCompactionEnded {
  /// Returns a new [EventSessionNextCompactionEnded] instance.
  EventSessionNextCompactionEnded({
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
        EventSessionNextCompactionEndedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextCompactionEndedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextCompactionEndedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextCompactionEnded &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextCompactionEnded.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextCompactionEndedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventSessionNextCompactionEndedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextCompactionEndedTypeEnum {
  @JsonValue(r'session.next.compaction.ended')
  sessionPeriodNextPeriodCompactionPeriodEnded(
    r'session.next.compaction.ended',
  ),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextCompactionEndedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
