//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_info.dart';
import 'package:opencode_sdk/src/model/question_tool.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionRequest {
  /// Returns a new [QuestionRequest] instance.
  QuestionRequest({
    required this.id,

    required this.sessionID,

    required this.questions,

    this.tool,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  /// Questions to ask
  @JsonKey(name: r'questions', required: true, includeIfNull: false)
  final List<QuestionInfo> questions;

  @JsonKey(name: r'tool', required: false, includeIfNull: false)
  final QuestionTool? tool;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, questions, tool],
              [other.id, other.sessionID, other.questions, other.tool],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, sessionID, questions, tool]);

  factory QuestionRequest.fromJson(Map<String, dynamic> json) =>
      _$QuestionRequestFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
