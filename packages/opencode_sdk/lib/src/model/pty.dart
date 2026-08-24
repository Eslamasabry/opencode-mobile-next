//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Pty {
  /// Returns a new [Pty] instance.
  Pty({
    required this.id,

    required this.title,

    required this.command,

    required this.args,

    required this.cwd,

    required this.status,

    required this.pid,

    this.exitCode,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'title', required: true, includeIfNull: false)
  final String title;

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final String command;

  @JsonKey(name: r'args', required: true, includeIfNull: false)
  final List<String> args;

  @JsonKey(name: r'cwd', required: true, includeIfNull: false)
  final String cwd;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PtyStatusEnum.unknownDefaultOpenApi,
  )
  final PtyStatusEnum status;

  // minimum: 0
  @JsonKey(name: r'pid', required: true, includeIfNull: false)
  final int pid;

  // minimum: 0
  @JsonKey(name: r'exitCode', required: false, includeIfNull: false)
  final int? exitCode;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Pty &&
            runtimeType == other.runtimeType &&
            equals(
              [id, title, command, args, cwd, status, pid, exitCode],
              [
                other.id,
                other.title,
                other.command,
                other.args,
                other.cwd,
                other.status,
                other.pid,
                other.exitCode,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        title,
        command,
        args,
        cwd,
        status,
        pid,
        exitCode,
      ]);

  factory Pty.fromJson(Map<String, dynamic> json) => _$PtyFromJson(json);

  Map<String, dynamic> toJson() => _$PtyToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PtyStatusEnum {
  @JsonValue(r'running')
  running(r'running'),
  @JsonValue(r'exited')
  exited(r'exited'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PtyStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
