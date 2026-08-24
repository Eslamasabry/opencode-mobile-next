//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_v2_replied_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_question_v2_replied.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventQuestionV2Replied {
  /// Returns a new [EventQuestionV2Replied] instance.
  EventQuestionV2Replied({
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
    unknownEnumValue: EventQuestionV2RepliedTypeEnum.unknownDefaultOpenApi,
  )
  final EventQuestionV2RepliedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final QuestionV2RepliedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventQuestionV2Replied &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventQuestionV2Replied.fromJson(Map<String, dynamic> json) =>
      _$EventQuestionV2RepliedFromJson(json);

  Map<String, dynamic> toJson() => _$EventQuestionV2RepliedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventQuestionV2RepliedTypeEnum {
  @JsonValue(r'question.v2.replied')
  questionPeriodV2PeriodReplied(r'question.v2.replied'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventQuestionV2RepliedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
