//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_compaction_started_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextCompactionStartedSyncEventData {
  /// Returns a new [SyncEventSessionNextCompactionStartedSyncEventData] instance.
  SyncEventSessionNextCompactionStartedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.reason,
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
    unknownEnumValue:
        SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum
            .unknownDefaultOpenApi,
  )
  final SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum reason;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextCompactionStartedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, reason],
              [other.timestamp, other.sessionID, other.messageID, other.reason],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, messageID, reason]);

  factory SyncEventSessionNextCompactionStartedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextCompactionStartedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextCompactionStartedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum {
  @JsonValue(r'auto')
  auto(r'auto'),
  @JsonValue(r'manual')
  manual(r'manual'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum(
    this.value,
  );

  final Object value;

  @override
  String toString() => value.toString();
}
