//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'command.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Command {
  /// Returns a new [Command] instance.
  Command({
    required this.name,

    this.description,

    this.agent,

    this.model,

    this.source_,

    required this.template,

    this.subtask,

    required this.hints,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final String? model;

  @JsonKey(
    name: r'source',
    required: false,
    includeIfNull: false,
    unknownEnumValue: CommandSource_Enum.unknownDefaultOpenApi,
  )
  final CommandSource_Enum? source_;

  @JsonKey(name: r'template', required: true, includeIfNull: false)
  final String template;

  @JsonKey(name: r'subtask', required: false, includeIfNull: false)
  final bool? subtask;

  @JsonKey(name: r'hints', required: true, includeIfNull: false)
  final List<String> hints;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Command &&
            runtimeType == other.runtimeType &&
            equals(
              [
                name,
                description,
                agent,
                model,
                source_,
                template,
                subtask,
                hints,
              ],
              [
                other.name,
                other.description,
                other.agent,
                other.model,
                other.source_,
                other.template,
                other.subtask,
                other.hints,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        name,
        description,
        agent,
        model,
        source_,
        template,
        subtask,
        hints,
      ]);

  factory Command.fromJson(Map<String, dynamic> json) =>
      _$CommandFromJson(json);

  Map<String, dynamic> toJson() => _$CommandToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum CommandSource_Enum {
  @JsonValue(r'command')
  command(r'command'),
  @JsonValue(r'mcp')
  mcp(r'mcp'),
  @JsonValue(r'skill')
  skill(r'skill'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const CommandSource_Enum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
