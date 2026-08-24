//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_compaction_ended_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextCompactionEndedSyncEventData {
  /// Returns a new [SyncEventSessionNextCompactionEndedSyncEventData] instance.
  SyncEventSessionNextCompactionEndedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.reason,

    required this.text,

    required this.recent,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'reason',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum
        .unknownDefaultOpenApi,
  )
  final SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum reason;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(name: r'recent', required: true, includeIfNull: false)
  final String recent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextCompactionEndedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, reason, text, recent],
              [
                other.timestamp,
                other.sessionID,
                other.messageID,
                other.reason,
                other.text,
                other.recent,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        messageID,
        reason,
        text,
        recent,
      ]);

  factory SyncEventSessionNextCompactionEndedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextCompactionEndedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextCompactionEndedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum {
  @JsonValue(r'auto')
  auto(r'auto'),
  @JsonValue(r'manual')
  manual(r'manual'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
