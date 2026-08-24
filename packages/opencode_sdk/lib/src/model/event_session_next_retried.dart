//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_retried_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_retried.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextRetried {
  /// Returns a new [EventSessionNextRetried] instance.
  EventSessionNextRetried({
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
    unknownEnumValue: EventSessionNextRetriedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextRetriedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextRetriedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextRetried &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextRetried.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextRetriedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextRetriedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextRetriedTypeEnum {
  @JsonValue(r'session.next.retried')
  sessionPeriodNextPeriodRetried(r'session.next.retried'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextRetriedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
