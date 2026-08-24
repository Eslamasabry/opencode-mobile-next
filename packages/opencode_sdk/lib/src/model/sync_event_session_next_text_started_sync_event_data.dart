//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_text_started_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextTextStartedSyncEventData {
  /// Returns a new [SyncEventSessionNextTextStartedSyncEventData] instance.
  SyncEventSessionNextTextStartedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.textID,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'textID', required: true, includeIfNull: false)
  final String textID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextTextStartedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, assistantMessageID, textID],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.textID,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, assistantMessageID, textID]);

  factory SyncEventSessionNextTextStartedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextTextStartedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextTextStartedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
