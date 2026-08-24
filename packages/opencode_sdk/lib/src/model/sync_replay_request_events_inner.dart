//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_replay_request_events_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncReplayRequestEventsInner {
  /// Returns a new [SyncReplayRequestEventsInner] instance.
  SyncReplayRequestEventsInner({
    required this.id,

    required this.aggregateID,

    required this.seq,

    required this.type,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'aggregateID', required: true, includeIfNull: false)
  final String aggregateID;

  // minimum: 0
  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final int seq;

  @JsonKey(name: r'type', required: true, includeIfNull: false)
  final String type;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final Object data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncReplayRequestEventsInner &&
            runtimeType == other.runtimeType &&
            equals(
              [id, aggregateID, seq, type, data],
              [other.id, other.aggregateID, other.seq, other.type, other.data],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, aggregateID, seq, type, data]);

  factory SyncReplayRequestEventsInner.fromJson(Map<String, dynamic> json) =>
      _$SyncReplayRequestEventsInnerFromJson(json);

  Map<String, dynamic> toJson() => _$SyncReplayRequestEventsInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
