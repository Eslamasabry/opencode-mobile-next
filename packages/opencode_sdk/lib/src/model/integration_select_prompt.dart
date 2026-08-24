//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_when.dart';
import 'package:opencode_sdk/src/model/integration_select_prompt_options_inner.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_select_prompt.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationSelectPrompt {
  /// Returns a new [IntegrationSelectPrompt] instance.
  IntegrationSelectPrompt({
    required this.type,

    required this.key,

    required this.message,

    required this.options,

    this.when_,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationSelectPromptTypeEnum.unknownDefaultOpenApi,
  )
  final IntegrationSelectPromptTypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'options', required: true, includeIfNull: false)
  final List<IntegrationSelectPromptOptionsInner> options;

  @JsonKey(name: r'when', required: false, includeIfNull: false)
  final IntegrationWhen? when_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationSelectPrompt &&
            runtimeType == other.runtimeType &&
            equals(
              [type, key, message, options, when_],
              [
                other.type,
                other.key,
                other.message,
                other.options,
                other.when_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, key, message, options, when_]);

  factory IntegrationSelectPrompt.fromJson(Map<String, dynamic> json) =>
      _$IntegrationSelectPromptFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationSelectPromptToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationSelectPromptTypeEnum {
  @JsonValue(r'select')
  select(r'select'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationSelectPromptTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
