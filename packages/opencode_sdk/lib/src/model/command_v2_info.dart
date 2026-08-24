//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'command_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class CommandV2Info {
  /// Returns a new [CommandV2Info] instance.
  CommandV2Info({
    required this.name,

    required this.template,

    this.description,

    this.agent,

    this.model,

    this.subtask,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'template', required: true, includeIfNull: false)
  final String template;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final ModelRef? model;

  @JsonKey(name: r'subtask', required: false, includeIfNull: false)
  final bool? subtask;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is CommandV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [name, template, description, agent, model, subtask],
              [
                other.name,
                other.template,
                other.description,
                other.agent,
                other.model,
                other.subtask,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, template, description, agent, model, subtask]);

  factory CommandV2Info.fromJson(Map<String, dynamic> json) =>
      _$CommandV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$CommandV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
