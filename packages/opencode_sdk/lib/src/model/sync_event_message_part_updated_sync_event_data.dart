//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_part.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_part_updated_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessagePartUpdatedSyncEventData {
  /// Returns a new [SyncEventMessagePartUpdatedSyncEventData] instance.
  SyncEventMessagePartUpdatedSyncEventData({
    required this.sessionID,

    required this.part_,

    required this.time,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'part', required: true, includeIfNull: false)
  final ModelPart part_;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final num time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessagePartUpdatedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, part_, time],
              [other.sessionID, other.part_, other.time],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, part_, time]);

  factory SyncEventMessagePartUpdatedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventMessagePartUpdatedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventMessagePartUpdatedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
