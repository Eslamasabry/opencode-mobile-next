//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_model_switched_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_model_switched.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextModelSwitched {
  /// Returns a new [EventSessionNextModelSwitched] instance.
  EventSessionNextModelSwitched({
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
        EventSessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextModelSwitchedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextModelSwitchedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextModelSwitched &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextModelSwitched.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextModelSwitchedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextModelSwitchedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextModelSwitchedTypeEnum {
  @JsonValue(r'session.next.model.switched')
  sessionPeriodNextPeriodModelPeriodSwitched(r'session.next.model.switched'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextModelSwitchedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
