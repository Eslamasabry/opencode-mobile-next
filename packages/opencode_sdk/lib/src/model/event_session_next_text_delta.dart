//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_next_text_delta_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_text_delta.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextTextDelta {
  /// Returns a new [EventSessionNextTextDelta] instance.
  EventSessionNextTextDelta({
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
    unknownEnumValue: EventSessionNextTextDeltaTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextTextDeltaTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SessionNextTextDeltaData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextTextDelta &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextTextDelta.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextTextDeltaFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextTextDeltaToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextTextDeltaTypeEnum {
  @JsonValue(r'session.next.text.delta')
  sessionPeriodNextPeriodTextPeriodDelta(r'session.next.text.delta'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextTextDeltaTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
