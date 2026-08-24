//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_v2_option.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_v2_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionV2Info {
  /// Returns a new [QuestionV2Info] instance.
  QuestionV2Info({
    required this.question,

    required this.header,

    required this.options,

    this.multiple,

    this.custom,
  });

  /// Complete question
  @JsonKey(name: r'question', required: true, includeIfNull: false)
  final String question;

  /// Very short label (max 30 chars)
  @JsonKey(name: r'header', required: true, includeIfNull: false)
  final String header;

  /// Available choices
  @JsonKey(name: r'options', required: true, includeIfNull: false)
  final List<QuestionV2Option> options;

  @JsonKey(name: r'multiple', required: false, includeIfNull: false)
  final bool? multiple;

  @JsonKey(name: r'custom', required: false, includeIfNull: false)
  final bool? custom;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionV2Info &&
            runtimeType == other.runtimeType &&
            equals(
              [question, header, options, multiple, custom],
              [
                other.question,
                other.header,
                other.options,
                other.multiple,
                other.custom,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([question, header, options, multiple, custom]);

  factory QuestionV2Info.fromJson(Map<String, dynamic> json) =>
      _$QuestionV2InfoFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionV2InfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
