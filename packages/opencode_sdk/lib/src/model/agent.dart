//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_prompt_async_request_model.dart';
import 'package:opencode_sdk/src/model/permission_rule.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'agent.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Agent {
  /// Returns a new [Agent] instance.
  Agent({
    required this.name,

    this.description,

    required this.mode,

    this.native_,

    this.hidden,

    this.topP,

    this.temperature,

    this.color,

    required this.permission,

    this.model,

    this.variant,

    this.prompt,

    required this.options,

    this.steps,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(
    name: r'mode',
    required: true,
    includeIfNull: false,
    unknownEnumValue: AgentModeEnum.unknownDefaultOpenApi,
  )
  final AgentModeEnum mode;

  @JsonKey(name: r'native', required: false, includeIfNull: false)
  final bool? native_;

  @JsonKey(name: r'hidden', required: false, includeIfNull: false)
  final bool? hidden;

  @JsonKey(name: r'topP', required: false, includeIfNull: false)
  final num? topP;

  @JsonKey(name: r'temperature', required: false, includeIfNull: false)
  final num? temperature;

  @JsonKey(name: r'color', required: false, includeIfNull: false)
  final String? color;

  @JsonKey(name: r'permission', required: true, includeIfNull: false)
  final List<PermissionRule> permission;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final SessionPromptAsyncRequestModel? model;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  @JsonKey(name: r'prompt', required: false, includeIfNull: false)
  final String? prompt;

  @JsonKey(name: r'options', required: true, includeIfNull: false)
  final Object options;

  @JsonKey(name: r'steps', required: false, includeIfNull: false)
  final num? steps;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Agent &&
            runtimeType == other.runtimeType &&
            equals(
              [
                name,
                description,
                mode,
                native_,
                hidden,
                topP,
                temperature,
                color,
                permission,
                model,
                variant,
                prompt,
                options,
                steps,
              ],
              [
                other.name,
                other.description,
                other.mode,
                other.native_,
                other.hidden,
                other.topP,
                other.temperature,
                other.color,
                other.permission,
                other.model,
                other.variant,
                other.prompt,
                other.options,
                other.steps,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        name,
        description,
        mode,
        native_,
        hidden,
        topP,
        temperature,
        color,
        permission,
        model,
        variant,
        prompt,
        options,
        steps,
      ]);

  factory Agent.fromJson(Map<String, dynamic> json) => _$AgentFromJson(json);

  Map<String, dynamic> toJson() => _$AgentToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum AgentModeEnum {
  @JsonValue(r'subagent')
  subagent(r'subagent'),
  @JsonValue(r'primary')
  primary(r'primary'),
  @JsonValue(r'all')
  all(r'all'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AgentModeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
