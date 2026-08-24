//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_rejected_schema2_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionRejectedSchema2Data {
  /// Returns a new [QuestionRejectedSchema2Data] instance.
  QuestionRejectedSchema2Data({
    required this.sessionID,

    required this.requestID,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'requestID', required: true, includeIfNull: false)
  final String requestID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionRejectedSchema2Data &&
            runtimeType == other.runtimeType &&
            equals([sessionID, requestID], [other.sessionID, other.requestID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, requestID]);

  factory QuestionRejectedSchema2Data.fromJson(Map<String, dynamic> json) =>
      _$QuestionRejectedSchema2DataFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionRejectedSchema2DataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
