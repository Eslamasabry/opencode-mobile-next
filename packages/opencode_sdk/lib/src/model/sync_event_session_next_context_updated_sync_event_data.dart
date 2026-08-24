//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_context_updated_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextContextUpdatedSyncEventData {
  /// Returns a new [SyncEventSessionNextContextUpdatedSyncEventData] instance.
  SyncEventSessionNextContextUpdatedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.text,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextContextUpdatedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, text],
              [other.timestamp, other.sessionID, other.messageID, other.text],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, messageID, text]);

  factory SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextContextUpdatedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextContextUpdatedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
