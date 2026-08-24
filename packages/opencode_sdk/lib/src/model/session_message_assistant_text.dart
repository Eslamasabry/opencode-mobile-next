//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant_text.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistantText {
  /// Returns a new [SessionMessageAssistantText] instance.
  SessionMessageAssistantText({
    required this.type,

    required this.id,

    required this.text,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageAssistantTextTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageAssistantTextTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistantText &&
            runtimeType == other.runtimeType &&
            equals([type, id, text], [other.type, other.id, other.text]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, text]);

  factory SessionMessageAssistantText.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAssistantTextFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageAssistantTextToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageAssistantTextTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageAssistantTextTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
