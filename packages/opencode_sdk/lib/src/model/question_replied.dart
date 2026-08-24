//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_replied.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionReplied {
  /// Returns a new [QuestionReplied] instance.
  QuestionReplied({
    required this.sessionID,

    required this.requestID,

    required this.answers,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'requestID', required: true, includeIfNull: false)
  final String requestID;

  @JsonKey(name: r'answers', required: true, includeIfNull: false)
  final List<List<String>> answers;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionReplied &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, requestID, answers],
              [other.sessionID, other.requestID, other.answers],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([sessionID, requestID, answers]);

  factory QuestionReplied.fromJson(Map<String, dynamic> json) =>
      _$QuestionRepliedFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionRepliedToJson(this);

  String toString() {
    return toJson().toString();
  }
}
