//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_replied_schema2_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionRepliedSchema2Data {
  /// Returns a new [QuestionRepliedSchema2Data] instance.
  QuestionRepliedSchema2Data({
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
        other is QuestionRepliedSchema2Data &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, requestID, answers],
              [other.sessionID, other.requestID, other.answers],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([sessionID, requestID, answers]);

  factory QuestionRepliedSchema2Data.fromJson(Map<String, dynamic> json) =>
      _$QuestionRepliedSchema2DataFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionRepliedSchema2DataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
