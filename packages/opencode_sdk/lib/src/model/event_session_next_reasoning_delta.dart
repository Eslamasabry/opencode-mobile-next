//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_next_reasoning_delta_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_reasoning_delta.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextReasoningDelta {
  /// Returns a new [EventSessionNextReasoningDelta] instance.
  EventSessionNextReasoningDelta({
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
    unknownEnumValue:
        EventSessionNextReasoningDeltaTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextReasoningDeltaTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SessionNextReasoningDeltaData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextReasoningDelta &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextReasoningDelta.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextReasoningDeltaFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextReasoningDeltaToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextReasoningDeltaTypeEnum {
  @JsonValue(r'session.next.reasoning.delta')
  sessionPeriodNextPeriodReasoningPeriodDelta(r'session.next.reasoning.delta'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextReasoningDeltaTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
