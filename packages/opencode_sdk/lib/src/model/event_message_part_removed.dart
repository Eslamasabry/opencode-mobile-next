//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_part_removed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_message_part_removed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventMessagePartRemoved {
  /// Returns a new [EventMessagePartRemoved] instance.
  EventMessagePartRemoved({
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
    unknownEnumValue: EventMessagePartRemovedTypeEnum.unknownDefaultOpenApi,
  )
  final EventMessagePartRemovedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventMessagePartRemovedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventMessagePartRemoved &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventMessagePartRemoved.fromJson(Map<String, dynamic> json) =>
      _$EventMessagePartRemovedFromJson(json);

  Map<String, dynamic> toJson() => _$EventMessagePartRemovedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventMessagePartRemovedTypeEnum {
  @JsonValue(r'message.part.removed')
  messagePeriodPartPeriodRemoved(r'message.part.removed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventMessagePartRemovedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
