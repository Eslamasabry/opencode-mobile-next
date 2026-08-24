//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tool_state_running_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_running.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStateRunning {
  /// Returns a new [ToolStateRunning] instance.
  ToolStateRunning({
    required this.status,

    required this.input,

    this.title,

    this.metadata,

    required this.time,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ToolStateRunningStatusEnum.unknownDefaultOpenApi,
  )
  final ToolStateRunningStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final ToolStateRunningTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStateRunning &&
            runtimeType == other.runtimeType &&
            equals(
              [status, input, title, metadata, time],
              [
                other.status,
                other.input,
                other.title,
                other.metadata,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([status, input, title, metadata, time]);

  factory ToolStateRunning.fromJson(Map<String, dynamic> json) =>
      _$ToolStateRunningFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStateRunningToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolStateRunningStatusEnum {
  @JsonValue(r'running')
  running(r'running'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolStateRunningStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
