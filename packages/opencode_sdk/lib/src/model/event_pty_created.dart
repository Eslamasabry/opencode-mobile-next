//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/pty_created_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_pty_created.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventPtyCreated {
  /// Returns a new [EventPtyCreated] instance.
  EventPtyCreated({
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
    unknownEnumValue: EventPtyCreatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventPtyCreatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final PtyCreatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventPtyCreated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventPtyCreated.fromJson(Map<String, dynamic> json) =>
      _$EventPtyCreatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventPtyCreatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventPtyCreatedTypeEnum {
  @JsonValue(r'pty.created')
  ptyPeriodCreated(r'pty.created'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventPtyCreatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
