//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_next_retry_error.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_retried_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextRetriedSyncEventData {
  /// Returns a new [SyncEventSessionNextRetriedSyncEventData] instance.
  SyncEventSessionNextRetriedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.attempt,

    required this.error,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'attempt', required: true, includeIfNull: false)
  final num attempt;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final SessionNextRetryError error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextRetriedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, attempt, error],
              [other.timestamp, other.sessionID, other.attempt, other.error],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, attempt, error]);

  factory SyncEventSessionNextRetriedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextRetriedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextRetriedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
