//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'prompt_agent_attachment.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PromptAgentAttachment {
  /// Returns a new [PromptAgentAttachment] instance.
  PromptAgentAttachment({required this.name, this.source_});

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'source', required: false, includeIfNull: false)
  final PromptSource? source_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PromptAgentAttachment &&
            runtimeType == other.runtimeType &&
            equals([name, source_], [other.name, other.source_]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([name, source_]);

  factory PromptAgentAttachment.fromJson(Map<String, dynamic> json) =>
      _$PromptAgentAttachmentFromJson(json);

  Map<String, dynamic> toJson() => _$PromptAgentAttachmentToJson(this);

  String toString() {
    return toJson().toString();
  }
}
