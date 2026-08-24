//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event_data_provider.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_called_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolCalledSyncEventData {
  /// Returns a new [SyncEventSessionNextToolCalledSyncEventData] instance.
  SyncEventSessionNextToolCalledSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.callID,

    required this.tool,

    required this.input,

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

  @JsonKey(name: r'tool', required: true, includeIfNull: false)
  final String tool;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'provider', required: true, includeIfNull: false)
  final SyncEventSessionNextToolCalledSyncEventDataProvider provider;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolCalledSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                callID,
                tool,
                input,
                provider,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.callID,
                other.tool,
                other.input,
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
        tool,
        input,
        provider,
      ]);

  factory SyncEventSessionNextToolCalledSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolCalledSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolCalledSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
