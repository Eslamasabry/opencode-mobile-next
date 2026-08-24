//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_rejected.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionRejected {
  /// Returns a new [QuestionRejected] instance.
  QuestionRejected({required this.sessionID, required this.requestID});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'requestID', required: true, includeIfNull: false)
  final String requestID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionRejected &&
            runtimeType == other.runtimeType &&
            equals([sessionID, requestID], [other.sessionID, other.requestID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, requestID]);

  factory QuestionRejected.fromJson(Map<String, dynamic> json) =>
      _$QuestionRejectedFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionRejectedToJson(this);

  String toString() {
    return toJson().toString();
  }
}
