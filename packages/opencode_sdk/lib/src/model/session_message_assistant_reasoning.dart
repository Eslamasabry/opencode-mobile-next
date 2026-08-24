//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_shell_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant_reasoning.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistantReasoning {
  /// Returns a new [SessionMessageAssistantReasoning] instance.
  SessionMessageAssistantReasoning({
    required this.type,

    required this.id,

    required this.text,

    this.providerMetadata,

    this.time,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SessionMessageAssistantReasoningTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageAssistantReasoningTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(name: r'providerMetadata', required: false, includeIfNull: false)
  final Map<String, Object>? providerMetadata;

  @JsonKey(name: r'time', required: false, includeIfNull: false)
  final SessionMessageShellTime? time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistantReasoning &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, text, providerMetadata, time],
              [
                other.type,
                other.id,
                other.text,
                other.providerMetadata,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, id, text, providerMetadata, time]);

  factory SessionMessageAssistantReasoning.fromJson(
    Map<String, dynamic> json,
  ) => _$SessionMessageAssistantReasoningFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessageAssistantReasoningToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageAssistantReasoningTypeEnum {
  @JsonValue(r'reasoning')
  reasoning(r'reasoning'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageAssistantReasoningTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
