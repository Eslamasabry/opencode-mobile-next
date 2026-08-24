//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_v2_tool.dart';
import 'package:opencode_sdk/src/model/question_v2_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_v2_asked_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionV2AskedData {
  /// Returns a new [QuestionV2AskedData] instance.
  QuestionV2AskedData({
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
  final List<QuestionV2Info> questions;

  @JsonKey(name: r'tool', required: false, includeIfNull: false)
  final QuestionV2Tool? tool;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionV2AskedData &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, questions, tool],
              [other.id, other.sessionID, other.questions, other.tool],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, sessionID, questions, tool]);

  factory QuestionV2AskedData.fromJson(Map<String, dynamic> json) =>
      _$QuestionV2AskedDataFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionV2AskedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
