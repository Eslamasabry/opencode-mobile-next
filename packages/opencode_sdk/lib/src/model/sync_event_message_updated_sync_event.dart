//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_updated_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_updated_sync_event.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessageUpdatedSyncEvent {
  /// Returns a new [SyncEventMessageUpdatedSyncEvent] instance.
  SyncEventMessageUpdatedSyncEvent({
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
        SyncEventMessageUpdatedSyncEventTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventMessageUpdatedSyncEventTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final num seq;

  @JsonKey(name: r'aggregateID', required: true, includeIfNull: false)
  final String aggregateID;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SyncEventMessageUpdatedSyncEventData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessageUpdatedSyncEvent &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, seq, aggregateID, data],
              [other.type, other.id, other.seq, other.aggregateID, other.data],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, id, seq, aggregateID, data]);

  factory SyncEventMessageUpdatedSyncEvent.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventMessageUpdatedSyncEventFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventMessageUpdatedSyncEventToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventMessageUpdatedSyncEventTypeEnum {
  @JsonValue(r'message.updated.1')
  messagePeriodUpdatedPeriod1(r'message.updated.1'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventMessageUpdatedSyncEventTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
