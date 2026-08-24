//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_ended_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_reasoning_ended_sync_event.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextReasoningEndedSyncEvent {
  /// Returns a new [SyncEventSessionNextReasoningEndedSyncEvent] instance.
  SyncEventSessionNextReasoningEndedSyncEvent({
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
    unknownEnumValue: SyncEventSessionNextReasoningEndedSyncEventTypeEnum
        .unknownDefaultOpenApi,
  )
  final SyncEventSessionNextReasoningEndedSyncEventTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final num seq;

  @JsonKey(name: r'aggregateID', required: true, includeIfNull: false)
  final String aggregateID;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SyncEventSessionNextReasoningEndedSyncEventData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextReasoningEndedSyncEvent &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, seq, aggregateID, data],
              [other.type, other.id, other.seq, other.aggregateID, other.data],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, id, seq, aggregateID, data]);

  factory SyncEventSessionNextReasoningEndedSyncEvent.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextReasoningEndedSyncEventFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextReasoningEndedSyncEventToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextReasoningEndedSyncEventTypeEnum {
  @JsonValue(r'session.next.reasoning.ended.1')
  sessionPeriodNextPeriodReasoningPeriodEndedPeriod1(
    r'session.next.reasoning.ended.1',
  ),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextReasoningEndedSyncEventTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
