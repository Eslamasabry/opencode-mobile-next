//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event_data_provider.dart';
import 'package:opencode_sdk/src/model/llm_tool_content.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_success_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolSuccessSyncEventData {
  /// Returns a new [SyncEventSessionNextToolSuccessSyncEventData] instance.
  SyncEventSessionNextToolSuccessSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.callID,

    required this.structured,

    required this.content,

    this.outputPaths,

    this.result,

    required this.provider,
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

  @JsonKey(name: r'outputPaths', required: false, includeIfNull: false)
  final List<String>? outputPaths;

  @JsonKey(name: r'result', required: false, includeIfNull: false)
  final Object? result;

  @JsonKey(name: r'provider', required: true, includeIfNull: false)
  final SyncEventSessionNextToolCalledSyncEventDataProvider provider;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolSuccessSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                callID,
                structured,
                content,
                outputPaths,
                result,
                provider,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.callID,
                other.structured,
                other.content,
                other.outputPaths,
                other.result,
                other.provider,
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
        outputPaths,
        result,
        provider,
      ]);

  factory SyncEventSessionNextToolSuccessSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolSuccessSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolSuccessSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
