//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/agent_part_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'agent_part_input.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AgentPartInput {
  /// Returns a new [AgentPartInput] instance.
  AgentPartInput({
    this.id,

    required this.type,

    required this.name,

    this.source_,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: AgentPartInputTypeEnum.unknownDefaultOpenApi,
  )
  final AgentPartInputTypeEnum type;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'source', required: false, includeIfNull: false)
  final AgentPartSource? source_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AgentPartInput &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, name, source_],
              [other.id, other.type, other.name, other.source_],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, name, source_]);

  factory AgentPartInput.fromJson(Map<String, dynamic> json) =>
      _$AgentPartInputFromJson(json);

  Map<String, dynamic> toJson() => _$AgentPartInputToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum AgentPartInputTypeEnum {
  @JsonValue(r'agent')
  agent(r'agent'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AgentPartInputTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
