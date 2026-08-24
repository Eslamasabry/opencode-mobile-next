//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_success_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_tool_success.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextToolSuccess {
  /// Returns a new [EventSessionNextToolSuccess] instance.
  EventSessionNextToolSuccess({
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
    unknownEnumValue: EventSessionNextToolSuccessTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextToolSuccessTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextToolSuccessSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextToolSuccess &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextToolSuccess.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextToolSuccessFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextToolSuccessToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextToolSuccessTypeEnum {
  @JsonValue(r'session.next.tool.success')
  sessionPeriodNextPeriodToolPeriodSuccess(r'session.next.tool.success'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextToolSuccessTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
