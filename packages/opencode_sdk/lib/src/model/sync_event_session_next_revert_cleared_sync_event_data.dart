//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_revert_cleared_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextRevertClearedSyncEventData {
  /// Returns a new [SyncEventSessionNextRevertClearedSyncEventData] instance.
  SyncEventSessionNextRevertClearedSyncEventData({
    required this.timestamp,

    required this.sessionID,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextRevertClearedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals([timestamp, sessionID], [other.timestamp, other.sessionID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([timestamp, sessionID]);

  factory SyncEventSessionNextRevertClearedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextRevertClearedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextRevertClearedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
