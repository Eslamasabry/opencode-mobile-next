//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_status_schema2_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_status.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionStatus {
  /// Returns a new [EventSessionStatus] instance.
  EventSessionStatus({
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
    unknownEnumValue: EventSessionStatusTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionStatusTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SessionStatusSchema2Data properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionStatus &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionStatus.fromJson(Map<String, dynamic> json) =>
      _$EventSessionStatusFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionStatusToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionStatusTypeEnum {
  @JsonValue(r'session.status')
  sessionPeriodStatus(r'session.status'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionStatusTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
