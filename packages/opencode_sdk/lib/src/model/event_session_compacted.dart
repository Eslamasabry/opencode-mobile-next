//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_steal_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_compacted.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionCompacted {
  /// Returns a new [EventSessionCompacted] instance.
  EventSessionCompacted({
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
    unknownEnumValue: EventSessionCompactedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionCompactedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncStealRequest properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionCompacted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionCompacted.fromJson(Map<String, dynamic> json) =>
      _$EventSessionCompactedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionCompactedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionCompactedTypeEnum {
  @JsonValue(r'session.compacted')
  sessionPeriodCompacted(r'session.compacted'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionCompactedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
