//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_tokens.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_step_ended_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextStepEndedSyncEventData {
  /// Returns a new [SyncEventSessionNextStepEndedSyncEventData] instance.
  SyncEventSessionNextStepEndedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.assistantMessageID,

    required this.finish,

    required this.cost,

    required this.tokens,

    this.snapshot,

    this.files,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'assistantMessageID', required: true, includeIfNull: false)
  final String assistantMessageID;

  @JsonKey(name: r'finish', required: true, includeIfNull: false)
  final String finish;

  @JsonKey(name: r'cost', required: true, includeIfNull: false)
  final num cost;

  @JsonKey(name: r'tokens', required: true, includeIfNull: false)
  final SessionTokens tokens;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final String? snapshot;

  @JsonKey(name: r'files', required: false, includeIfNull: false)
  final List<String>? files;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextStepEndedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                timestamp,
                sessionID,
                assistantMessageID,
                finish,
                cost,
                tokens,
                snapshot,
                files,
              ],
              [
                other.timestamp,
                other.sessionID,
                other.assistantMessageID,
                other.finish,
                other.cost,
                other.tokens,
                other.snapshot,
                other.files,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        timestamp,
        sessionID,
        assistantMessageID,
        finish,
        cost,
        tokens,
        snapshot,
        files,
      ]);

  factory SyncEventSessionNextStepEndedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextStepEndedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextStepEndedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
