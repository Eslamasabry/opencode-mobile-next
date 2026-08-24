//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_v2_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_question_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionQuestionList200Response {
  /// Returns a new [V2SessionQuestionList200Response] instance.
  V2SessionQuestionList200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<QuestionV2Request> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionQuestionList200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionQuestionList200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2SessionQuestionList200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2SessionQuestionList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
