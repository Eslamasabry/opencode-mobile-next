//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_select_prompt_options_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationSelectPromptOptionsInner {
  /// Returns a new [IntegrationSelectPromptOptionsInner] instance.
  IntegrationSelectPromptOptionsInner({
    required this.label,

    required this.value,

    this.hint,
  });

  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  @JsonKey(name: r'value', required: true, includeIfNull: false)
  final String value;

  @JsonKey(name: r'hint', required: false, includeIfNull: false)
  final String? hint;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationSelectPromptOptionsInner &&
            runtimeType == other.runtimeType &&
            equals(
              [label, value, hint],
              [other.label, other.value, other.hint],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([label, value, hint]);

  factory IntegrationSelectPromptOptionsInner.fromJson(
    Map<String, dynamic> json,
  ) => _$IntegrationSelectPromptOptionsInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$IntegrationSelectPromptOptionsInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
