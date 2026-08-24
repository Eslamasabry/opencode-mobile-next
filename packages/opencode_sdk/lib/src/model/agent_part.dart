//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/agent_part_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'agent_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AgentPart {
  /// Returns a new [AgentPart] instance.
  AgentPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.name,

    this.source_,
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
    unknownEnumValue: AgentPartTypeEnum.unknownDefaultOpenApi,
  )
  final AgentPartTypeEnum type;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'source', required: false, includeIfNull: false)
  final AgentPartSource? source_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AgentPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, name, source_],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.name,
                other.source_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, sessionID, messageID, type, name, source_]);

  factory AgentPart.fromJson(Map<String, dynamic> json) =>
      _$AgentPartFromJson(json);

  Map<String, dynamic> toJson() => _$AgentPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum AgentPartTypeEnum {
  @JsonValue(r'agent')
  agent(r'agent'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const AgentPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
