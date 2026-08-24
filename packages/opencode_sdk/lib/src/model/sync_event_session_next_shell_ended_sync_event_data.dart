//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_shell_ended_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextShellEndedSyncEventData {
  /// Returns a new [SyncEventSessionNextShellEndedSyncEventData] instance.
  SyncEventSessionNextShellEndedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.callID,

    required this.output,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final String output;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextShellEndedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, callID, output],
              [other.timestamp, other.sessionID, other.callID, other.output],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, callID, output]);

  factory SyncEventSessionNextShellEndedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextShellEndedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextShellEndedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
