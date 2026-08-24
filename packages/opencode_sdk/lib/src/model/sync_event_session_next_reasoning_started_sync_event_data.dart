//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_reasoning_started_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextReasoningStartedSyncEventData {
  /// Returns a new [SyncEventSessionNextReasoningStartedSyncEventData] instance.
  SyncEventSessionNextReasoningStartedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.reasoningID,

    this.providerMetadata,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'reasoningID', required: true, includeIfNull: false)
  final String reasoningID;

  @JsonKey(name: r'providerMetadata', required: false, includeIfNull: false)
  final Map<String, Object>? providerMetadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextReasoningStartedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                reasoningID,
                providerMetadata,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.reasoningID,
                other.providerMetadata,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        assistantMessageID,
        reasoningID,
        providerMetadata,
      ]);

  factory SyncEventSessionNextReasoningStartedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextReasoningStartedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextReasoningStartedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
