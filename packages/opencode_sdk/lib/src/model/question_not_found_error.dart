//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'question_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class QuestionNotFoundError {
  /// Returns a new [QuestionNotFoundError] instance.
  QuestionNotFoundError({
    required this.tag,

    required this.requestID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: QuestionNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final QuestionNotFoundErrorTagEnum tag;

  @JsonKey(name: r'requestID', required: true, includeIfNull: false)
  final String requestID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is QuestionNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, requestID, message],
              [other.tag, other.requestID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, requestID, message]);

  factory QuestionNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$QuestionNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$QuestionNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum QuestionNotFoundErrorTagEnum {
  @JsonValue(r'QuestionNotFoundError')
  questionNotFoundError(r'QuestionNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const QuestionNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
