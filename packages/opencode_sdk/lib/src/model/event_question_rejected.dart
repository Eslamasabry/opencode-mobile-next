//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_rejected_schema2_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_question_rejected.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventQuestionRejected {
  /// Returns a new [EventQuestionRejected] instance.
  EventQuestionRejected({
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
    unknownEnumValue: EventQuestionRejectedTypeEnum.unknownDefaultOpenApi,
  )
  final EventQuestionRejectedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final QuestionRejectedSchema2Data properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventQuestionRejected &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventQuestionRejected.fromJson(Map<String, dynamic> json) =>
      _$EventQuestionRejectedFromJson(json);

  Map<String, dynamic> toJson() => _$EventQuestionRejectedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventQuestionRejectedTypeEnum {
  @JsonValue(r'question.rejected')
  questionPeriodRejected(r'question.rejected'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventQuestionRejectedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
