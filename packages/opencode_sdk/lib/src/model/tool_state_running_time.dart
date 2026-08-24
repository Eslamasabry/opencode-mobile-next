//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_running_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStateRunningTime {
  /// Returns a new [ToolStateRunningTime] instance.
  ToolStateRunningTime({required this.start});

  // minimum: 0
  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final int start;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStateRunningTime &&
            runtimeType == other.runtimeType &&
            equals([start], [other.start]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([start]);

  factory ToolStateRunningTime.fromJson(Map<String, dynamic> json) =>
      _$ToolStateRunningTimeFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStateRunningTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
