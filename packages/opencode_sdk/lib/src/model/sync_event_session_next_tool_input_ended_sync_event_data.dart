//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_input_ended_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolInputEndedSyncEventData {
  /// Returns a new [SyncEventSessionNextToolInputEndedSyncEventData] instance.
  SyncEventSessionNextToolInputEndedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.callID,

    required this.text,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolInputEndedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, assistantMessageID, callID, text],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.callID,
                other.text,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        assistantMessageID,
        callID,
        text,
      ]);

  factory SyncEventSessionNextToolInputEndedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolInputEndedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolInputEndedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
