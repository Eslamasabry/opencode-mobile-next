//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_next_tool_input_delta_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_tool_input_delta.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextToolInputDelta {
  /// Returns a new [EventSessionNextToolInputDelta] instance.
  EventSessionNextToolInputDelta({
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
        EventSessionNextToolInputDeltaTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextToolInputDeltaTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SessionNextToolInputDeltaData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextToolInputDelta &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextToolInputDelta.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextToolInputDeltaFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextToolInputDeltaToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextToolInputDeltaTypeEnum {
  @JsonValue(r'session.next.tool.input.delta')
  sessionPeriodNextPeriodToolPeriodInputPeriodDelta(
    r'session.next.tool.input.delta',
  ),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextToolInputDeltaTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
