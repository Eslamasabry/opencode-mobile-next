//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_assistant_tool_time.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union020.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_tool_provider.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant_tool.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistantTool {
  /// Returns a new [SessionMessageAssistantTool] instance.
  SessionMessageAssistantTool({
    required this.type,

    required this.id,

    required this.name,

    this.provider,

    required this.state,

    required this.time,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageAssistantToolTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageAssistantToolTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'provider', required: false, includeIfNull: false)
  final SessionMessageAssistantToolProvider? provider;

  @JsonKey(name: r'state', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion020 state;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageAssistantToolTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistantTool &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, name, provider, state, time],
              [
                other.type,
                other.id,
                other.name,
                other.provider,
                other.state,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, id, name, provider, state, time]);

  factory SessionMessageAssistantTool.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAssistantToolFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageAssistantToolToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageAssistantToolTypeEnum {
  @JsonValue(r'tool')
  tool(r'tool'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageAssistantToolTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
