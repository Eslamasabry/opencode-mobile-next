//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_part_updated_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_message_part_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventMessagePartUpdated {
  /// Returns a new [EventMessagePartUpdated] instance.
  EventMessagePartUpdated({
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
    unknownEnumValue: EventMessagePartUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventMessagePartUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventMessagePartUpdatedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventMessagePartUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventMessagePartUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventMessagePartUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventMessagePartUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventMessagePartUpdatedTypeEnum {
  @JsonValue(r'message.part.updated')
  messagePeriodPartPeriodUpdated(r'message.part.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventMessagePartUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
