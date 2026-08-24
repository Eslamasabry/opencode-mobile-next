//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_removed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_removed_sync_event.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessageRemovedSyncEvent {
  /// Returns a new [SyncEventMessageRemovedSyncEvent] instance.
  SyncEventMessageRemovedSyncEvent({
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
        SyncEventMessageRemovedSyncEventTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventMessageRemovedSyncEventTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final num seq;

  @JsonKey(name: r'aggregateID', required: true, includeIfNull: false)
  final String aggregateID;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SyncEventMessageRemovedSyncEventData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessageRemovedSyncEvent &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, seq, aggregateID, data],
              [other.type, other.id, other.seq, other.aggregateID, other.data],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, id, seq, aggregateID, data]);

  factory SyncEventMessageRemovedSyncEvent.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventMessageRemovedSyncEventFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventMessageRemovedSyncEventToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventMessageRemovedSyncEventTypeEnum {
  @JsonValue(r'message.removed.1')
  messagePeriodRemovedPeriod1(r'message.removed.1'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventMessageRemovedSyncEventTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
