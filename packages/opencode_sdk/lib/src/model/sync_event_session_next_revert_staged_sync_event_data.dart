//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/revert_state.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_revert_staged_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextRevertStagedSyncEventData {
  /// Returns a new [SyncEventSessionNextRevertStagedSyncEventData] instance.
  SyncEventSessionNextRevertStagedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.revert,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'revert', required: true, includeIfNull: false)
  final RevertState revert;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextRevertStagedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, revert],
              [other.timestamp, other.sessionID, other.revert],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([timestamp, sessionID, revert]);

  factory SyncEventSessionNextRevertStagedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextRevertStagedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextRevertStagedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
