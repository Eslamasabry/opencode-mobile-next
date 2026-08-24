//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_config.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union003.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'agent_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AgentConfig {
  /// Returns a new [AgentConfig] instance.
  AgentConfig({
    this.model,

    this.variant,

    this.temperature,

    this.topP,

    this.prompt,

    this.tools,

    this.disable,

    this.description,

    this.mode,

    this.hidden,

    this.options,

    this.color,

    this.steps,

    this.maxSteps,

    this.permission,
    Map<String, Object?> additionalProperties = const {},
  }) : _additionalProperties = Map.unmodifiable(additionalProperties);

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final String? model;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  @JsonKey(name: r'temperature', required: false, includeIfNull: false)
  final num? temperature;

  @JsonKey(name: r'top_p', required: false, includeIfNull: false)
  final num? topP;

  @JsonKey(name: r'prompt', required: false, includeIfNull: false)
  final String? prompt;

  @JsonKey(name: r'tools', required: false, includeIfNull: false)
  final Map<String, bool>? tools;

  @JsonKey(name: r'disable', required: false, includeIfNull: false)
  final bool? disable;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(
    name: r'mode',
    required: false,
    includeIfNull: false,
    unknownEnumValue: AgentConfigModeEnum.unknownDefaultOpenApi,
  )
  final AgentConfigModeEnum? mode;

  @JsonKey(name: r'hidden', required: false, includeIfNull: false)
  final bool? hidden;

  @JsonKey(name: r'options', required: false, includeIfNull: false)
  final Object? options;

  @JsonKey(name: r'color', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion003? color;

  @JsonKey(name: r'steps', required: false, includeIfNull: false)
  final int? steps;

  @JsonKey(name: r'maxSteps', required: false, includeIfNull: false)
  final int? maxSteps;

  @JsonKey(name: r'permission', required: false, includeIfNull: false)
  final PermissionConfig? permission;

  Map<String, Object?> _additionalProperties;

  @JsonKey(includeFromJson: false, includeToJson: false)
  Map<String, Object?> get additionalProperties => _additionalProperties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AgentConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [
                model,
                variant,
                temperature,
                topP,
                prompt,
                tools,
                disable,
                description,
                mode,
                hidden,
                options,
                color,
                steps,
                maxSteps,
                permission,
              ],
              [
                other.model,
                other.variant,
                other.temperature,
                other.topP,
                other.prompt,
                other.tools,
                other.disable,
                other.description,
                other.mode,
                other.hidden,
                other.options,
                other.color,
                other.steps,
                other.maxSteps,
                other.permission,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        model,
        variant,
        temperature,
        topP,
        prompt,
        tools,
        disable,
        description,
        mode,
        hidden,
        options,
        color,
        steps,
        maxSteps,
        permission,
      ]);

  factory AgentConfig.fromJson(Map<String, dynamic> json) {
    final value = _$AgentConfigFromJson(json);
    const knownKeys = <String>{
      r'model',
      r'variant',
      r'temperature',
      r'top_p',
      r'prompt',
      r'tools',
      r'disable',
      r'description',
      r'mode',
      r'hidden',
      r'options',
      r'color',
      r'steps',
      r'maxSteps',
      r'permission',
    };
    value._additionalProperties = Map.unmodifiable({
      for (final entry in json.entries)
        if (!knownKeys.contains(entry.key)) entry.key: entry.value,
    });
    return value;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    for (final entry in additionalProperties.entries) entry.key: entry.value,
    ..._$AgentConfigToJson(this),
  };

  String toString() {
    return toJson().toString();
  }
}

enum AgentConfigModeEnum {
  @JsonValue(r'subagent')
  subagent(r'subagent'),
  @JsonValue(r'primary')
  primary(r'primary'),
  @JsonValue(r'all')
  all(r'all'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AgentConfigModeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
