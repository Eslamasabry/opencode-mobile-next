//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_created_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_created.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionCreated {
  /// Returns a new [EventSessionCreated] instance.
  EventSessionCreated({
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
    unknownEnumValue: EventSessionCreatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionCreatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionCreatedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionCreated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionCreated.fromJson(Map<String, dynamic> json) =>
      _$EventSessionCreatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionCreatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionCreatedTypeEnum {
  @JsonValue(r'session.created')
  sessionPeriodCreated(r'session.created'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionCreatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
