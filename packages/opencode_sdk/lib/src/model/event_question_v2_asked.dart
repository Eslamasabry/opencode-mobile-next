//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_v2_asked_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_question_v2_asked.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventQuestionV2Asked {
  /// Returns a new [EventQuestionV2Asked] instance.
  EventQuestionV2Asked({
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
    unknownEnumValue: EventQuestionV2AskedTypeEnum.unknownDefaultOpenApi,
  )
  final EventQuestionV2AskedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final QuestionV2AskedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventQuestionV2Asked &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventQuestionV2Asked.fromJson(Map<String, dynamic> json) =>
      _$EventQuestionV2AskedFromJson(json);

  Map<String, dynamic> toJson() => _$EventQuestionV2AskedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventQuestionV2AskedTypeEnum {
  @JsonValue(r'question.v2.asked')
  questionPeriodV2PeriodAsked(r'question.v2.asked'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventQuestionV2AskedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
