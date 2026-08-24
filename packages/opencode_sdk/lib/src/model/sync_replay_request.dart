//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_replay_request_events_inner.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_replay_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncReplayRequest {
  /// Returns a new [SyncReplayRequest] instance.
  SyncReplayRequest({required this.directory, required this.events});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'events', required: true, includeIfNull: false)
  final List<SyncReplayRequestEventsInner> events;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncReplayRequest &&
            runtimeType == other.runtimeType &&
            equals([directory, events], [other.directory, other.events]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([directory, events]);

  factory SyncReplayRequest.fromJson(Map<String, dynamic> json) =>
      _$SyncReplayRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SyncReplayRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
