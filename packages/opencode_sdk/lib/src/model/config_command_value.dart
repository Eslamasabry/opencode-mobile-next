//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_command_value.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigCommandValue {
  /// Returns a new [ConfigCommandValue] instance.
  ConfigCommandValue({
    required this.template,

    this.description,

    this.agent,

    this.model,

    this.variant,

    this.subtask,
  });

  @JsonKey(name: r'template', required: true, includeIfNull: false)
  final String template;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final String? model;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  @JsonKey(name: r'subtask', required: false, includeIfNull: false)
  final bool? subtask;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigCommandValue &&
            runtimeType == other.runtimeType &&
            equals(
              [template, description, agent, model, variant, subtask],
              [
                other.template,
                other.description,
                other.agent,
                other.model,
                other.variant,
                other.subtask,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        template,
        description,
        agent,
        model,
        variant,
        subtask,
      ]);

  factory ConfigCommandValue.fromJson(Map<String, dynamic> json) =>
      _$ConfigCommandValueFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigCommandValueToJson(this);

  String toString() {
    return toJson().toString();
  }
}
