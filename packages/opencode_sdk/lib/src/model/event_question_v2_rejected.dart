//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/question_rejected_schema2_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_question_v2_rejected.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventQuestionV2Rejected {
  /// Returns a new [EventQuestionV2Rejected] instance.
  EventQuestionV2Rejected({
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
    unknownEnumValue: EventQuestionV2RejectedTypeEnum.unknownDefaultOpenApi,
  )
  final EventQuestionV2RejectedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final QuestionRejectedSchema2Data properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventQuestionV2Rejected &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventQuestionV2Rejected.fromJson(Map<String, dynamic> json) =>
      _$EventQuestionV2RejectedFromJson(json);

  Map<String, dynamic> toJson() => _$EventQuestionV2RejectedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventQuestionV2RejectedTypeEnum {
  @JsonValue(r'question.v2.rejected')
  questionPeriodV2PeriodRejected(r'question.v2.rejected'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventQuestionV2RejectedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
