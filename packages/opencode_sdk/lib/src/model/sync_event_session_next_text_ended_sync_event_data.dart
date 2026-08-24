//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_text_ended_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextTextEndedSyncEventData {
  /// Returns a new [SyncEventSessionNextTextEndedSyncEventData] instance.
  SyncEventSessionNextTextEndedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.textID,

    required this.text,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'textID', required: true, includeIfNull: false)
  final String textID;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextTextEndedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, assistantMessageID, textID, text],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.textID,
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
        textID,
        text,
      ]);

  factory SyncEventSessionNextTextEndedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextTextEndedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextTextEndedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
