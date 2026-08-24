//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_v2_option.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionV2Option {
  /// Returns a new [QuestionV2Option] instance.
  QuestionV2Option({required this.label, required this.description});

  /// Display text (1-5 words, concise)
  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  /// Explanation of choice
  @JsonKey(name: r'description', required: true, includeIfNull: false)
  final String description;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionV2Option &&
            runtimeType == other.runtimeType &&
            equals([label, description], [other.label, other.description]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([label, description]);

  factory QuestionV2Option.fromJson(Map<String, dynamic> json) =>
      _$QuestionV2OptionFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionV2OptionToJson(this);

  String toString() {
    return toJson().toString();
  }
}
