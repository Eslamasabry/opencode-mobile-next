//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_prompt_async_request_model.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_shell_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionShellRequest {
  /// Returns a new [SessionShellRequest] instance.
  SessionShellRequest({
    this.messageID,

    required this.agent,

    this.model,

    required this.command,
  });

  @JsonKey(name: r'messageID', required: false, includeIfNull: false)
  final String? messageID;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final SessionPromptAsyncRequestModel? model;

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final String command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionShellRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [messageID, agent, model, command],
              [other.messageID, other.agent, other.model, other.command],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([messageID, agent, model, command]);

  factory SessionShellRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionShellRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionShellRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
