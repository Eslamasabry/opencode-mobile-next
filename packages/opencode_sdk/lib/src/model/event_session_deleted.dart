//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_created_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_deleted.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionDeleted {
  /// Returns a new [EventSessionDeleted] instance.
  EventSessionDeleted({
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
    unknownEnumValue: EventSessionDeletedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionDeletedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionCreatedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionDeleted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionDeleted.fromJson(Map<String, dynamic> json) =>
      _$EventSessionDeletedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionDeletedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionDeletedTypeEnum {
  @JsonValue(r'session.deleted')
  sessionPeriodDeleted(r'session.deleted'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionDeletedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
