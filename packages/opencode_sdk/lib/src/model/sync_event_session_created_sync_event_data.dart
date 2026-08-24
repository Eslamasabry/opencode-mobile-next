//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_created_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionCreatedSyncEventData {
  /// Returns a new [SyncEventSessionCreatedSyncEventData] instance.
  SyncEventSessionCreatedSyncEventData({
    required this.sessionID,

    required this.info,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'info', required: true, includeIfNull: false)
  final Session info;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionCreatedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals([sessionID, info], [other.sessionID, other.info]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, info]);

  factory SyncEventSessionCreatedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionCreatedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionCreatedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
