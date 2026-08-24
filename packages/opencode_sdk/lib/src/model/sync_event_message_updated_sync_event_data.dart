//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/message.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_updated_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessageUpdatedSyncEventData {
  /// Returns a new [SyncEventMessageUpdatedSyncEventData] instance.
  SyncEventMessageUpdatedSyncEventData({
    required this.sessionID,

    required this.info,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'info', required: true, includeIfNull: false)
  final Message info;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessageUpdatedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals([sessionID, info], [other.sessionID, other.info]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, info]);

  factory SyncEventMessageUpdatedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventMessageUpdatedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventMessageUpdatedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
