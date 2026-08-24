//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/llm_tool_content.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_progress_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolProgressSyncEventData {
  /// Returns a new [SyncEventSessionNextToolProgressSyncEventData] instance.
  SyncEventSessionNextToolProgressSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.callID,

    required this.structured,

    required this.content,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'structured', required: true, includeIfNull: false)
  final Object structured;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final List<LLMToolContent> content;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolProgressSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                callID,
                structured,
                content,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.callID,
                other.structured,
                other.content,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        assistantMessageID,
        callID,
        structured,
        content,
      ]);

  factory SyncEventSessionNextToolProgressSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolProgressSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolProgressSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
