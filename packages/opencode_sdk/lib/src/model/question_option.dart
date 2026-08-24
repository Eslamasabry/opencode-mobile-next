//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_option.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionOption {
  /// Returns a new [QuestionOption] instance.
  QuestionOption({required this.label, required this.description});

  /// Display text (1-5 words, concise)
  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  /// Explanation of choice
  @JsonKey(name: r'description', required: true, includeIfNull: false)
  final String description;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionOption &&
            runtimeType == other.runtimeType &&
            equals([label, description], [other.label, other.description]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([label, description]);

  factory QuestionOption.fromJson(Map<String, dynamic> json) =>
      _$QuestionOptionFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionOptionToJson(this);

  String toString() {
    return toJson().toString();
  }
}
