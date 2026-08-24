//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_error_unknown.dart';
import 'package:opencode_sdk/src/model/llm_tool_content.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_tool_state_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageToolStateError {
  /// Returns a new [SessionMessageToolStateError] instance.
  SessionMessageToolStateError({
    required this.status,

    required this.input,

    required this.content,

    required this.structured,

    required this.error,

    this.result,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SessionMessageToolStateErrorStatusEnum.unknownDefaultOpenApi,
  )
  final SessionMessageToolStateErrorStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final List<LLMToolContent> content;

  @JsonKey(name: r'structured', required: true, includeIfNull: false)
  final Object structured;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final SessionErrorUnknown error;

  @JsonKey(name: r'result', required: false, includeIfNull: false)
  final Object? result;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageToolStateError &&
            runtimeType == other.runtimeType &&
            equals(
              [status, input, content, structured, error, result],
              [
                other.status,
                other.input,
                other.content,
                other.structured,
                other.error,
                other.result,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([status, input, content, structured, error, result]);

  factory SessionMessageToolStateError.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageToolStateErrorFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageToolStateErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageToolStateErrorStatusEnum {
  @JsonValue(r'error')
  error(r'error'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageToolStateErrorStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
