//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt_agent_attachment.dart';
import 'package:opencode_sdk/src/model/prompt_file_attachment.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'prompt.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Prompt {
  /// Returns a new [Prompt] instance.
  Prompt({required this.text, this.files, this.agents});

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(name: r'files', required: false, includeIfNull: false)
  final List<PromptFileAttachment>? files;

  @JsonKey(name: r'agents', required: false, includeIfNull: false)
  final List<PromptAgentAttachment>? agents;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Prompt &&
            runtimeType == other.runtimeType &&
            equals(
              [text, files, agents],
              [other.text, other.files, other.agents],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([text, files, agents]);

  factory Prompt.fromJson(Map<String, dynamic> json) => _$PromptFromJson(json);

  Map<String, dynamic> toJson() => _$PromptToJson(this);

  String toString() {
    return toJson().toString();
  }
}
