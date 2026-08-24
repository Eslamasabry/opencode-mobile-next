//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_replied_schema2_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_question_replied.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventQuestionReplied {
  /// Returns a new [EventQuestionReplied] instance.
  EventQuestionReplied({
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
    unknownEnumValue: EventQuestionRepliedTypeEnum.unknownDefaultOpenApi,
  )
  final EventQuestionRepliedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final QuestionRepliedSchema2Data properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventQuestionReplied &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventQuestionReplied.fromJson(Map<String, dynamic> json) =>
      _$EventQuestionRepliedFromJson(json);

  Map<String, dynamic> toJson() => _$EventQuestionRepliedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventQuestionRepliedTypeEnum {
  @JsonValue(r'question.replied')
  questionPeriodReplied(r'question.replied'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventQuestionRepliedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
