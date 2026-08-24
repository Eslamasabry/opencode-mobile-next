//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt_agent_attachment.dart';
import 'package:opencode_sdk/src/model/prompt_file_attachment.dart';
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_user.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageUser {
  /// Returns a new [SessionMessageUser] instance.
  SessionMessageUser({
    required this.id,

    this.metadata,

    required this.time,

    required this.text,

    this.files,

    this.agents,

    required this.type,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageAgentSwitchedTime time;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(name: r'files', required: false, includeIfNull: false)
  final List<PromptFileAttachment>? files;

  @JsonKey(name: r'agents', required: false, includeIfNull: false)
  final List<PromptAgentAttachment>? agents;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageUserTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageUserTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageUser &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, time, text, files, agents, type],
              [
                other.id,
                other.metadata,
                other.time,
                other.text,
                other.files,
                other.agents,
                other.type,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, time, text, files, agents, type]);

  factory SessionMessageUser.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageUserFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageUserToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageUserTypeEnum {
  @JsonValue(r'user')
  user(r'user'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageUserTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
