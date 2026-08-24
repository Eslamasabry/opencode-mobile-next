//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_step_started_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextStepStartedSyncEventData {
  /// Returns a new [SyncEventSessionNextStepStartedSyncEventData] instance.
  SyncEventSessionNextStepStartedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.agent,

    required this.model,

    this.snapshot,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  @JsonKey(name: r'model', required: true, includeIfNull: false)
  final ModelRef model;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final String? snapshot;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextStepStartedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                agent,
                model,
                snapshot,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.agent,
                other.model,
                other.snapshot,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        assistantMessageID,
        agent,
        model,
        snapshot,
      ]);

  factory SyncEventSessionNextStepStartedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextStepStartedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextStepStartedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
