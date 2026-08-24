//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_error_unknown.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event_data_provider.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_failed_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolFailedSyncEventData {
  /// Returns a new [SyncEventSessionNextToolFailedSyncEventData] instance.
  SyncEventSessionNextToolFailedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.callID,

    required this.error,

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

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final SessionErrorUnknown error;

  @JsonKey(name: r'result', required: false, includeIfNull: false)
  final Object? result;

  @JsonKey(name: r'provider', required: true, includeIfNull: false)
  final SyncEventSessionNextToolCalledSyncEventDataProvider provider;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolFailedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                callID,
                error,
                result,
                provider,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.callID,
                other.error,
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
        error,
        result,
        provider,
      ]);

  factory SyncEventSessionNextToolFailedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolFailedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolFailedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
