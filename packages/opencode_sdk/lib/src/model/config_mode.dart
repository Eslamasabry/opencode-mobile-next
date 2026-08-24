//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/agent_config.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_mode.g.dart';

// ignore_for_file: unused_import

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigMode {
  /// Returns a new [ConfigMode] instance.
  ConfigMode({
    this.build,

    this.plan,
    Map<String, AgentConfig> additionalProperties = const {},
  }) : _additionalProperties = Map.unmodifiable(additionalProperties);

  @JsonKey(name: r'build', required: false, includeIfNull: false)
  final AgentConfig? build;

  @JsonKey(name: r'plan', required: false, includeIfNull: false)
  final AgentConfig? plan;

  Map<String, AgentConfig> _additionalProperties;

  @JsonKey(includeFromJson: false, includeToJson: false)
  Map<String, AgentConfig> get additionalProperties => _additionalProperties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigMode &&
            runtimeType == other.runtimeType &&
            equals([build, plan], [other.build, other.plan]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([build, plan]);

  factory ConfigMode.fromJson(Map<String, dynamic> json) {
    final value = _$ConfigModeFromJson(json);
    const knownKeys = <String>{r'build', r'plan'};
    value._additionalProperties = Map.unmodifiable({
      for (final entry in json.entries)
        if (!knownKeys.contains(entry.key))
          entry.key: AgentConfig.fromJson(entry.value as Map<String, dynamic>),
    });
    return value;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    for (final entry in additionalProperties.entries)
      entry.key: entry.value.toJson(),
    ..._$ConfigModeToJson(this),
  };

  String toString() {
    return toJson().toString();
  }
}
