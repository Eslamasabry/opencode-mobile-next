//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_command_request_parts_inner.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_command_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionCommandRequest {
  /// Returns a new [SessionCommandRequest] instance.
  SessionCommandRequest({
    this.messageID,

    this.agent,

    this.model,

    required this.arguments,

    required this.command,

    this.variant,

    this.parts,
  });

  @JsonKey(name: r'messageID', required: false, includeIfNull: false)
  final String? messageID;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final String? model;

  @JsonKey(name: r'arguments', required: true, includeIfNull: false)
  final String arguments;

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final String command;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  @JsonKey(name: r'parts', required: false, includeIfNull: false)
  final List<SessionCommandRequestPartsInner>? parts;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionCommandRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [messageID, agent, model, arguments, command, variant, parts],
              [
                other.messageID,
                other.agent,
                other.model,
                other.arguments,
                other.command,
                other.variant,
                other.parts,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        messageID,
        agent,
        model,
        arguments,
        command,
        variant,
        parts,
      ]);

  factory SessionCommandRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionCommandRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionCommandRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
