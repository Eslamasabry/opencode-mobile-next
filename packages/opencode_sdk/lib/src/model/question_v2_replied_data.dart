//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_v2_replied_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionV2RepliedData {
  /// Returns a new [QuestionV2RepliedData] instance.
  QuestionV2RepliedData({
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
        other is QuestionV2RepliedData &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, requestID, answers],
              [other.sessionID, other.requestID, other.answers],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([sessionID, requestID, answers]);

  factory QuestionV2RepliedData.fromJson(Map<String, dynamic> json) =>
      _$QuestionV2RepliedDataFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionV2RepliedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
