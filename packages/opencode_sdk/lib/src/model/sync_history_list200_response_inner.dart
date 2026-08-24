//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_history_list200_response_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncHistoryList200ResponseInner {
  /// Returns a new [SyncHistoryList200ResponseInner] instance.
  SyncHistoryList200ResponseInner({
    required this.id,

    required this.aggregateId,

    required this.seq,

    required this.type,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'aggregate_id', required: true, includeIfNull: false)
  final String aggregateId;

  // minimum: 0
  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final int seq;

  @JsonKey(name: r'type', required: true, includeIfNull: false)
  final String type;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final Object data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncHistoryList200ResponseInner &&
            runtimeType == other.runtimeType &&
            equals(
              [id, aggregateId, seq, type, data],
              [other.id, other.aggregateId, other.seq, other.type, other.data],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, aggregateId, seq, type, data]);

  factory SyncHistoryList200ResponseInner.fromJson(Map<String, dynamic> json) =>
      _$SyncHistoryList200ResponseInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncHistoryList200ResponseInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
