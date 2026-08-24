//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_v2_reply.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionV2Reply {
  /// Returns a new [QuestionV2Reply] instance.
  QuestionV2Reply({required this.answers});

  /// User answers in order of questions (each answer is an array of selected labels)
  @JsonKey(name: r'answers', required: true, includeIfNull: false)
  final List<List<String>> answers;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionV2Reply &&
            runtimeType == other.runtimeType &&
            equals([answers], [other.answers]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([answers]);

  factory QuestionV2Reply.fromJson(Map<String, dynamic> json) =>
      _$QuestionV2ReplyFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionV2ReplyToJson(this);

  String toString() {
    return toJson().toString();
  }
}
