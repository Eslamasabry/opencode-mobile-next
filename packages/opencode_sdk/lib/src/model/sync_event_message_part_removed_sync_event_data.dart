//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_part_removed_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessagePartRemovedSyncEventData {
  /// Returns a new [SyncEventMessagePartRemovedSyncEventData] instance.
  SyncEventMessagePartRemovedSyncEventData({
    required this.sessionID,

    required this.messageID,

    required this.partID,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'partID', required: true, includeIfNull: false)
  final String partID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessagePartRemovedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, messageID, partID],
              [other.sessionID, other.messageID, other.partID],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, messageID, partID]);

  factory SyncEventMessagePartRemovedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventMessagePartRemovedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventMessagePartRemovedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
