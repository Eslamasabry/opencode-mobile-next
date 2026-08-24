//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_v2_tool.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionV2Tool {
  /// Returns a new [QuestionV2Tool] instance.
  QuestionV2Tool({required this.messageID, required this.callID});

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionV2Tool &&
            runtimeType == other.runtimeType &&
            equals([messageID, callID], [other.messageID, other.callID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([messageID, callID]);

  factory QuestionV2Tool.fromJson(Map<String, dynamic> json) =>
      _$QuestionV2ToolFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionV2ToolToJson(this);

  String toString() {
    return toJson().toString();
  }
}
