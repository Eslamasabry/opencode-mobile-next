//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_error_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStateErrorTime {
  /// Returns a new [ToolStateErrorTime] instance.
  ToolStateErrorTime({required this.start, required this.end});

  // minimum: 0
  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final int start;

  // minimum: 0
  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final int end;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStateErrorTime &&
            runtimeType == other.runtimeType &&
            equals([start, end], [other.start, other.end]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([start, end]);

  factory ToolStateErrorTime.fromJson(Map<String, dynamic> json) =>
      _$ToolStateErrorTimeFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStateErrorTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
