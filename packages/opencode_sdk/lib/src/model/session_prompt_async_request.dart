//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_prompt_async_request_model.dart';
import 'package:opencode_sdk/src/model/output_format.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union086.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_prompt_async_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionPromptAsyncRequest {
  /// Returns a new [SessionPromptAsyncRequest] instance.
  SessionPromptAsyncRequest({
    this.messageID,

    this.model,

    this.agent,

    this.noReply,

    this.tools,

    this.format,

    this.system,

    this.variant,

    required this.parts,
  });

  @JsonKey(name: r'messageID', required: false, includeIfNull: false)
  final String? messageID;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final SessionPromptAsyncRequestModel? model;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'noReply', required: false, includeIfNull: false)
  final bool? noReply;

  @JsonKey(name: r'tools', required: false, includeIfNull: false)
  final Map<String, bool>? tools;

  @JsonKey(name: r'format', required: false, includeIfNull: false)
  final OutputFormat? format;

  @JsonKey(name: r'system', required: false, includeIfNull: false)
  final String? system;

  @JsonKey(name: r'variant', required: false, includeIfNull: false)
  final String? variant;

  @JsonKey(name: r'parts', required: true, includeIfNull: false)
  final List<OpencodeSdkRawUnion086> parts;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionPromptAsyncRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [
                messageID,
                model,
                agent,
                noReply,
                tools,
                format,
                system,
                variant,
                parts,
              ],
              [
                other.messageID,
                other.model,
                other.agent,
                other.noReply,
                other.tools,
                other.format,
                other.system,
                other.variant,
                other.parts,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        messageID,
        model,
        agent,
        noReply,
        tools,
        format,
        system,
        variant,
        parts,
      ]);

  factory SessionPromptAsyncRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionPromptAsyncRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionPromptAsyncRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
