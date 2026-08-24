//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_moved_sync_event_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextMovedSyncEventData {
  /// Returns a new [SyncEventSessionNextMovedSyncEventData] instance.
  SyncEventSessionNextMovedSyncEventData({
    required this.timestamp,

    required this.sessionID,

    required this.location,

    this.subdirectory,
  });

  @JsonKey(name: r'timestamp', required: true, includeIfNull: false)
  final num timestamp;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationRef location;

  @JsonKey(name: r'subdirectory', required: false, includeIfNull: false)
  final String? subdirectory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextMovedSyncEventData &&
            runtimeType == other.runtimeType &&
            equals(
              [timestamp, sessionID, location, subdirectory],
              [
                other.timestamp,
                other.sessionID,
                other.location,
                other.subdirectory,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([timestamp, sessionID, location, subdirectory]);

  factory SyncEventSessionNextMovedSyncEventData.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextMovedSyncEventDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextMovedSyncEventDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
