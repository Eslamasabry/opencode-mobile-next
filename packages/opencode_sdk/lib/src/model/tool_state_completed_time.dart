//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_completed_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStateCompletedTime {
  /// Returns a new [ToolStateCompletedTime] instance.
  ToolStateCompletedTime({
    required this.start,

    required this.end,

    this.compacted,
  });

  // minimum: 0
  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final int start;

  // minimum: 0
  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final int end;

  // minimum: 0
  @JsonKey(name: r'compacted', required: false, includeIfNull: false)
  final int? compacted;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStateCompletedTime &&
            runtimeType == other.runtimeType &&
            equals(
              [start, end, compacted],
              [other.start, other.end, other.compacted],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([start, end, compacted]);

  factory ToolStateCompletedTime.fromJson(Map<String, dynamic> json) =>
      _$ToolStateCompletedTimeFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStateCompletedTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
