//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_shell_started_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextShellStartedSyncEventData {
  /// Returns a new [SyncEventSessionNextShellStartedSyncEventData] instance.
  SyncEventSessionNextShellStartedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.callID,

    required this.command,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final String command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextShellStartedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, callID, command],
              [
                other.timestamp,
                other.sessionID,
                other.messageID,
                other.callID,
                other.command,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, messageID, callID, command]);

  factory SyncEventSessionNextShellStartedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextShellStartedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextShellStartedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
