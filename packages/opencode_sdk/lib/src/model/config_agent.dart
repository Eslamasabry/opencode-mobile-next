//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/agent_config.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_agent.g.dart';

// ignore_for_file: unused_import

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigAgent {
  /// Returns a new [ConfigAgent] instance.
  ConfigAgent({
    this.plan,

    this.build,

    this.general,

    this.explore,

    this.title,

    this.summary,

    this.compaction,
    Map<String, AgentConfig> additionalProperties = const {},
  }) : _additionalProperties = Map.unmodifiable(additionalProperties);

  @JsonKey(name: r'plan', required: false, includeIfNull: false)
  final AgentConfig? plan;

  @JsonKey(name: r'build', required: false, includeIfNull: false)
  final AgentConfig? build;

  @JsonKey(name: r'general', required: false, includeIfNull: false)
  final AgentConfig? general;

  @JsonKey(name: r'explore', required: false, includeIfNull: false)
  final AgentConfig? explore;

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final AgentConfig? title;

  @JsonKey(name: r'summary', required: false, includeIfNull: false)
  final AgentConfig? summary;

  @JsonKey(name: r'compaction', required: false, includeIfNull: false)
  final AgentConfig? compaction;

  Map<String, AgentConfig> _additionalProperties;

  @JsonKey(includeFromJson: false, includeToJson: false)
  Map<String, AgentConfig> get additionalProperties => _additionalProperties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigAgent &&
            runtimeType == other.runtimeType &&
            equals(
              [plan, build, general, explore, title, summary, compaction],
              [
                other.plan,
                other.build,
                other.general,
                other.explore,
                other.title,
                other.summary,
                other.compaction,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        plan,
        build,
        general,
        explore,
        title,
        summary,
        compaction,
      ]);

  factory ConfigAgent.fromJson(Map<String, dynamic> json) {
    final value = _$ConfigAgentFromJson(json);
    const knownKeys = <String>{
      r'plan',
      r'build',
      r'general',
      r'explore',
      r'title',
      r'summary',
      r'compaction',
    };
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
    ..._$ConfigAgentToJson(this),
  };

  String toString() {
    return toJson().toString();
  }
}
