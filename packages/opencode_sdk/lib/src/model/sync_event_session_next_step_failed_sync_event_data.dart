//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_error_unknown.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_step_failed_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextStepFailedSyncEventData {
  /// Returns a new [SyncEventSessionNextStepFailedSyncEventData] instance.
  SyncEventSessionNextStepFailedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.error,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final SessionErrorUnknown error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextStepFailedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, assistantMessageID, error],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.error,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, assistantMessageID, error]);

  factory SyncEventSessionNextStepFailedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextStepFailedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextStepFailedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
