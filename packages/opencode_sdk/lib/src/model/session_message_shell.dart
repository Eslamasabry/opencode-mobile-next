//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_shell_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_shell.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageShell {
  /// Returns a new [SessionMessageShell] instance.
  SessionMessageShell({
    required this.id,

    this.metadata,

    required this.time,

    required this.type,

    required this.callID,

    required this.command,

    required this.output,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageShellTime time;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageShellTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageShellTypeEnum type;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final String command;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final String output;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageShell &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, time, type, callID, command, output],
              [
                other.id,
                other.metadata,
                other.time,
                other.type,
                other.callID,
                other.command,
                other.output,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, time, type, callID, command, output]);

  factory SessionMessageShell.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageShellFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageShellToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageShellTypeEnum {
  @JsonValue(r'shell')
  shell(r'shell'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageShellTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
