//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_agent_switched_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextAgentSwitchedSyncEventData {
  /// Returns a new [SyncEventSessionNextAgentSwitchedSyncEventData] instance.
  SyncEventSessionNextAgentSwitchedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.agent,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextAgentSwitchedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, agent],
              [other.timestamp, other.sessionID, other.messageID, other.agent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, messageID, agent]);

  factory SyncEventSessionNextAgentSwitchedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextAgentSwitchedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextAgentSwitchedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
