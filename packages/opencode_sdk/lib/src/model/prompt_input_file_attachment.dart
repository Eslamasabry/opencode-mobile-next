//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'prompt_input_file_attachment.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PromptInputFileAttachment {
  /// Returns a new [PromptInputFileAttachment] instance.
  PromptInputFileAttachment({
    required this.uri,

    this.name,

    this.description,

    this.source_,
  });

  @JsonKey(name: r'uri', required: true, includeIfNull: false)
  final String uri;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'source', required: false, includeIfNull: false)
  final PromptSource? source_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PromptInputFileAttachment &&
            runtimeType == other.runtimeType &&
            equals(
              [uri, name, description, source_],
              [other.uri, other.name, other.description, other.source_],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([uri, name, description, source_]);

  factory PromptInputFileAttachment.fromJson(Map<String, dynamic> json) =>
      _$PromptInputFileAttachmentFromJson(json);

  Map<String, dynamic> toJson() => _$PromptInputFileAttachmentToJson(this);

  String toString() {
    return toJson().toString();
  }
}
