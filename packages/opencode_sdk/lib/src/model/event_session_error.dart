//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/event_session_error_properties.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionError {
  /// Returns a new [EventSessionError] instance.
  EventSessionError({
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
    unknownEnumValue: EventSessionErrorTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionErrorTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final EventSessionErrorProperties properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionError &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionError.fromJson(Map<String, dynamic> json) =>
      _$EventSessionErrorFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionErrorTypeEnum {
  @JsonValue(r'session.error')
  sessionPeriodError(r'session.error'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionErrorTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
