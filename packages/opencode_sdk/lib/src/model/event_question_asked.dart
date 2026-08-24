//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_asked_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_question_asked.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventQuestionAsked {
  /// Returns a new [EventQuestionAsked] instance.
  EventQuestionAsked({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventQuestionAskedTypeEnum.unknownDefaultOpenApi,
  )
  final EventQuestionAskedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final QuestionAskedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventQuestionAsked &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventQuestionAsked.fromJson(Map<String, dynamic> json) =>
      _$EventQuestionAskedFromJson(json);

  Map<String, dynamic> toJson() => _$EventQuestionAskedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventQuestionAskedTypeEnum {
  @JsonValue(r'question.asked')
  questionPeriodAsked(r'question.asked'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventQuestionAskedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
