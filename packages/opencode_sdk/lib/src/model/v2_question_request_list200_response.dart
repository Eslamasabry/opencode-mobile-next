//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_v2_request.dart';
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_question_request_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2QuestionRequestList200Response {
  /// Returns a new [V2QuestionRequestList200Response] instance.
  V2QuestionRequestList200Response({
    required this.location,

    required this.data,
  });

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationInfo location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<QuestionV2Request> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2QuestionRequestList200Response &&
            runtimeType == other.runtimeType &&
            equals([location, data], [other.location, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([location, data]);

  factory V2QuestionRequestList200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2QuestionRequestList200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2QuestionRequestList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
