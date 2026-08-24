//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_error_unknown.dart';
import 'package:opencode_sdk/src/model/session_message_shell_time.dart';
import 'package:opencode_sdk/src/model/session_tokens.dart';
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union021.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_snapshot.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistant {
  /// Returns a new [SessionMessageAssistant] instance.
  SessionMessageAssistant({
    required this.id,

    this.metadata,

    required this.time,

    required this.type,

    required this.agent,

    required this.model,

    required this.content,

    this.snapshot,

    this.finish,

    this.cost,

    this.tokens,

    this.error,
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
    unknownEnumValue: SessionMessageAssistantTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageAssistantTypeEnum type;

  @JsonKey(name: r'agent', required: true, includeIfNull: false)
  final String agent;

  @JsonKey(name: r'model', required: true, includeIfNull: false)
  final ModelRef model;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final List<OpencodeSdkRawUnion021> content;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final SessionMessageAssistantSnapshot? snapshot;

  @JsonKey(name: r'finish', required: false, includeIfNull: false)
  final String? finish;

  @JsonKey(name: r'cost', required: false, includeIfNull: false)
  final num? cost;

  @JsonKey(name: r'tokens', required: false, includeIfNull: false)
  final SessionTokens? tokens;

  @JsonKey(name: r'error', required: false, includeIfNull: false)
  final SessionErrorUnknown? error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistant &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                metadata,
                time,
                type,
                agent,
                model,
                content,
                snapshot,
                finish,
                cost,
                tokens,
                error,
              ],
              [
                other.id,
                other.metadata,
                other.time,
                other.type,
                other.agent,
                other.model,
                other.content,
                other.snapshot,
                other.finish,
                other.cost,
                other.tokens,
                other.error,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        metadata,
        time,
        type,
        agent,
        model,
        content,
        snapshot,
        finish,
        cost,
        tokens,
        error,
      ]);

  factory SessionMessageAssistant.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAssistantFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageAssistantToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageAssistantTypeEnum {
  @JsonValue(r'assistant')
  assistant(r'assistant'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageAssistantTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
