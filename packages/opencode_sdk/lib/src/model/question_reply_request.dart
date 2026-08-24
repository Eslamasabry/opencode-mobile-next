//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_reply_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionReplyRequest {
  /// Returns a new [QuestionReplyRequest] instance.
  QuestionReplyRequest({required this.answers});

  /// User answers in order of questions (each answer is an array of selected labels)
  @JsonKey(name: r'answers', required: true, includeIfNull: false)
  final List<List<String>> answers;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionReplyRequest &&
            runtimeType == other.runtimeType &&
            equals([answers], [other.answers]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([answers]);

  factory QuestionReplyRequest.fromJson(Map<String, dynamic> json) =>
      _$QuestionReplyRequestFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionReplyRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
