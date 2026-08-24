//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_when.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_text_prompt.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationTextPrompt {
  /// Returns a new [IntegrationTextPrompt] instance.
  IntegrationTextPrompt({
    required this.type,

    required this.key,

    required this.message,

    this.placeholder,

    this.when_,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationTextPromptTypeEnum.unknownDefaultOpenApi,
  )
  final IntegrationTextPromptTypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'placeholder', required: false, includeIfNull: false)
  final String? placeholder;

  @JsonKey(name: r'when', required: false, includeIfNull: false)
  final IntegrationWhen? when_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationTextPrompt &&
            runtimeType == other.runtimeType &&
            equals(
              [type, key, message, placeholder, when_],
              [
                other.type,
                other.key,
                other.message,
                other.placeholder,
                other.when_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, key, message, placeholder, when_]);

  factory IntegrationTextPrompt.fromJson(Map<String, dynamic> json) =>
      _$IntegrationTextPromptFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationTextPromptToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationTextPromptTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationTextPromptTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
