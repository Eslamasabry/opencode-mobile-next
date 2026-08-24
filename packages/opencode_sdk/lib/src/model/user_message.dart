//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/user_message_time.dart';
import 'package:opencode_sdk/src/model/user_message_summary.dart';
import 'package:opencode_sdk/src/model/user_message_model.dart';
import 'package:opencode_sdk/src/model/output_format.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'user_message.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UserMessage {
  /// Returns a new [UserMessage] instance.
  UserMessage({
    required this.id,

    required this.sessionID,

    required this.role,

    required this.time,

    this.format,

    this.summary,

    required this.agent,

    required this.model,

    this.system,

    this.tools,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(
    name: r'role',
    required: true,
    includeIfNull: false,
    unknownEnumValue: UserMessageRoleEnum.unknownDefaultOpenApi,
  )
  final UserMessageRoleEnum role;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final UserMessageTime time;

  @JsonKey(name: r'format', required: false, includeIfNull: false)
  final OutputFormat? format;

  @JsonKey(name: r'summary', required: false, includeIfNull: false)
  final UserMessageSummary? summary;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  @JsonKey(name: r'model', required: true, includeIfNull: false)
  final UserMessageModel model;

  @JsonKey(name: r'system', required: false, includeIfNull: false)
  final String? system;

  @JsonKey(name: r'tools', required: false, includeIfNull: false)
  final Map<String, bool>? tools;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UserMessage &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                sessionID,
                role,
                time,
                format,
                summary,
                agent,
                model,
                system,
                tools,
              ],
              [
                other.id,
                other.sessionID,
                other.role,
                other.time,
                other.format,
                other.summary,
                other.agent,
                other.model,
                other.system,
                other.tools,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        role,
        time,
        format,
        summary,
        agent,
        model,
        system,
        tools,
      ]);

  factory UserMessage.fromJson(Map<String, dynamic> json) =>
      _$UserMessageFromJson(json);

  Map<String, dynamic> toJson() => _$UserMessageToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum UserMessageRoleEnum {
  @JsonValue(r'user')
  user(r'user'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const UserMessageRoleEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
