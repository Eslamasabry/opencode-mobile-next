//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_progress_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_tool_progress.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextToolProgress {
  /// Returns a new [EventSessionNextToolProgress] instance.
  EventSessionNextToolProgress({
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
        EventSessionNextToolProgressTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextToolProgressTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextToolProgressSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextToolProgress &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextToolProgress.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextToolProgressFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextToolProgressToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextToolProgressTypeEnum {
  @JsonValue(r'session.next.tool.progress')
  sessionPeriodNextPeriodToolPeriodProgress(r'session.next.tool.progress'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextToolProgressTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
