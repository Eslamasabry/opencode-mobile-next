//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt_file_attachment.dart';
import 'package:opencode_sdk/src/model/llm_tool_content.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_tool_state_completed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageToolStateCompleted {
  /// Returns a new [SessionMessageToolStateCompleted] instance.
  SessionMessageToolStateCompleted({
    required this.status,

    required this.input,

    this.attachments,

    required this.content,

    this.outputPaths,

    required this.structured,

    this.result,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SessionMessageToolStateCompletedStatusEnum.unknownDefaultOpenApi,
  )
  final SessionMessageToolStateCompletedStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'attachments', required: false, includeIfNull: false)
  final List<PromptFileAttachment>? attachments;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final List<LLMToolContent> content;

  @JsonKey(name: r'outputPaths', required: false, includeIfNull: false)
  final List<String>? outputPaths;

  @JsonKey(name: r'structured', required: true, includeIfNull: false)
  final Object structured;

  @JsonKey(name: r'result', required: false, includeIfNull: false)
  final Object? result;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageToolStateCompleted &&
            runtimeType == other.runtimeType &&
            equals(
              [
                status,
                input,
                attachments,
                content,
                outputPaths,
                structured,
                result,
              ],
              [
                other.status,
                other.input,
                other.attachments,
                other.content,
                other.outputPaths,
                other.structured,
                other.result,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        status,
        input,
        attachments,
        content,
        outputPaths,
        structured,
        result,
      ]);

  factory SessionMessageToolStateCompleted.fromJson(
    Map<String, dynamic> json,
  ) => _$SessionMessageToolStateCompletedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessageToolStateCompletedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageToolStateCompletedStatusEnum {
  @JsonValue(r'completed')
  completed(r'completed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageToolStateCompletedStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
