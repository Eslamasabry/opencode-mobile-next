//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_failed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_failed_sync_event.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolFailedSyncEvent {
  /// Returns a new [SyncEventSessionNextToolFailedSyncEvent] instance.
  SyncEventSessionNextToolFailedSyncEvent({
    required this.type,

    required this.id,

    required this.seq,

    required this.aggregateID,

    required this.data,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SyncEventSessionNextToolFailedSyncEventTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventSessionNextToolFailedSyncEventTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final num seq;

  @JsonKey(name: r'aggregateID', required: true, includeIfNull: false)
  final String aggregateID;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SyncEventSessionNextToolFailedSyncEventData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolFailedSyncEvent &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, seq, aggregateID, data],
              [other.type, other.id, other.seq, other.aggregateID, other.data],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, id, seq, aggregateID, data]);

  factory SyncEventSessionNextToolFailedSyncEvent.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolFailedSyncEventFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolFailedSyncEventToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextToolFailedSyncEventTypeEnum {
  @JsonValue(r'session.next.tool.failed.1')
  sessionPeriodNextPeriodToolPeriodFailedPeriod1(r'session.next.tool.failed.1'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextToolFailedSyncEventTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
