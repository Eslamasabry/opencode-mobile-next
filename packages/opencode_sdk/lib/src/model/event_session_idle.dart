//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_steal_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_idle.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionIdle {
  /// Returns a new [EventSessionIdle] instance.
  EventSessionIdle({
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
    unknownEnumValue: EventSessionIdleTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionIdleTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncStealRequest properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionIdle &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionIdle.fromJson(Map<String, dynamic> json) =>
      _$EventSessionIdleFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionIdleToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionIdleTypeEnum {
  @JsonValue(r'session.idle')
  sessionPeriodIdle(r'session.idle'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionIdleTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
