//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_prompt_async_request_model.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'subtask_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SubtaskPart {
  /// Returns a new [SubtaskPart] instance.
  SubtaskPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.prompt,

    required this.description,

    required this.agent,

    this.model,

    this.command,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SubtaskPartTypeEnum.unknownDefaultOpenApi,
  )
  final SubtaskPartTypeEnum type;

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
        other is SubtaskPart &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                sessionID,
                messageID,
                type,
                prompt,
                description,
                agent,
                model,
                command,
              ],
              [
                other.id,
                other.sessionID,
                other.messageID,
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
        sessionID,
        messageID,
        type,
        prompt,
        description,
        agent,
        model,
        command,
      ]);

  factory SubtaskPart.fromJson(Map<String, dynamic> json) =>
      _$SubtaskPartFromJson(json);

  Map<String, dynamic> toJson() => _$SubtaskPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SubtaskPartTypeEnum {
  @JsonValue(r'subtask')
  subtask(r'subtask'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SubtaskPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
