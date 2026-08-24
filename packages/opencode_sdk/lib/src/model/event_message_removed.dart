//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_removed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_message_removed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventMessageRemoved {
  /// Returns a new [EventMessageRemoved] instance.
  EventMessageRemoved({
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
    unknownEnumValue: EventMessageRemovedTypeEnum.unknownDefaultOpenApi,
  )
  final EventMessageRemovedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventMessageRemovedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventMessageRemoved &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventMessageRemoved.fromJson(Map<String, dynamic> json) =>
      _$EventMessageRemovedFromJson(json);

  Map<String, dynamic> toJson() => _$EventMessageRemovedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventMessageRemovedTypeEnum {
  @JsonValue(r'message.removed')
  messagePeriodRemoved(r'message.removed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventMessageRemovedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
