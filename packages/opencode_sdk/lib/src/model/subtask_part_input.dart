//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_prompt_async_request_model.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'subtask_part_input.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SubtaskPartInput {
  /// Returns a new [SubtaskPartInput] instance.
  SubtaskPartInput({
    this.id,

    required this.type,

    required this.prompt,

    required this.description,

    required this.agent,

    this.model,

    this.command,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SubtaskPartInputTypeEnum.unknownDefaultOpenApi,
  )
  final SubtaskPartInputTypeEnum type;

  @JsonKey(name: r'prompt', required: true, includeIfNull: false)
  final String prompt;

  @JsonKey(name: r'description', required: true, includeIfNull: false)
  final String description;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final SessionPromptAsyncRequestModel? model;

  @JsonKey(name: r'command', required: false, includeIfNull: false)
  final String? command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SubtaskPartInput &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, prompt, description, agent, model, command],
              [
                other.id,
                other.type,
                other.prompt,
                other.description,
                other.agent,
                other.model,
                other.command,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        type,
        prompt,
        description,
        agent,
        model,
        command,
      ]);

  factory SubtaskPartInput.fromJson(Map<String, dynamic> json) =>
      _$SubtaskPartInputFromJson(json);

  Map<String, dynamic> toJson() => _$SubtaskPartInputToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SubtaskPartInputTypeEnum {
  @JsonValue(r'subtask')
  subtask(r'subtask'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SubtaskPartInputTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
