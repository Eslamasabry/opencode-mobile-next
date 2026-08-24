//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_model_switched_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextModelSwitchedSyncEventData {
  /// Returns a new [SyncEventSessionNextModelSwitchedSyncEventData] instance.
  SyncEventSessionNextModelSwitchedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.messageID,

    required this.model,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'model', required: true, includeIfNull: false)
  final ModelRef model;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextModelSwitchedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, messageID, model],
              [other.timestamp, other.sessionID, other.messageID, other.model],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, messageID, model]);

  factory SyncEventSessionNextModelSwitchedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextModelSwitchedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextModelSwitchedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
