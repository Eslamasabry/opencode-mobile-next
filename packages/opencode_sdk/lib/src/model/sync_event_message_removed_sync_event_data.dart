//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_removed_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessageRemovedSyncEventData {
  /// Returns a new [SyncEventMessageRemovedSyncEventData] instance.
  SyncEventMessageRemovedSyncEventData({
    required this.sessionID,

    required this.messageID,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessageRemovedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals([sessionID, messageID], [other.sessionID, other.messageID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, messageID]);

  factory SyncEventMessageRemovedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventMessageRemovedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventMessageRemovedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
