//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/llm_tool_content.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_tool_state_running.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageToolStateRunning {
  /// Returns a new [SessionMessageToolStateRunning] instance.
  SessionMessageToolStateRunning({
    required this.status,

    required this.input,

    required this.structured,

    required this.content,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SessionMessageToolStateRunningStatusEnum.unknownDefaultOpenApi,
  )
  final SessionMessageToolStateRunningStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'structured', required: true, includeIfNull: false)
  final Object structured;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final List<LLMToolContent> content;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageToolStateRunning &&
            runtimeType == other.runtimeType &&
            equals(
              [status, input, structured, content],
              [other.status, other.input, other.structured, other.content],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([status, input, structured, content]);

  factory SessionMessageToolStateRunning.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageToolStateRunningFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageToolStateRunningToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageToolStateRunningStatusEnum {
  @JsonValue(r'running')
  running(r'running'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageToolStateRunningStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
