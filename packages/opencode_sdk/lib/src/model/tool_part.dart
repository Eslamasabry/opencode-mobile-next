//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tool_state.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolPart {
  /// Returns a new [ToolPart] instance.
  ToolPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.callID,

    required this.tool,

    required this.state,

    this.metadata,
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
    unknownEnumValue: ToolPartTypeEnum.unknownDefaultOpenApi,
  )
  final ToolPartTypeEnum type;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'tool', required: true, includeIfNull: false)
  final String tool;

  @JsonKey(name: r'state', required: true, includeIfNull: false)
  final ToolState state;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, callID, tool, state, metadata],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.callID,
                other.tool,
                other.state,
                other.metadata,
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
        callID,
        tool,
        state,
        metadata,
      ]);

  factory ToolPart.fromJson(Map<String, dynamic> json) =>
      _$ToolPartFromJson(json);

  Map<String, dynamic> toJson() => _$ToolPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolPartTypeEnum {
  @JsonValue(r'tool')
  tool(r'tool'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
