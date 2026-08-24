//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_reference_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventReferenceUpdated {
  /// Returns a new [EventReferenceUpdated] instance.
  EventReferenceUpdated({
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
    unknownEnumValue: EventReferenceUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventReferenceUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventReferenceUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventReferenceUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventReferenceUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventReferenceUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventReferenceUpdatedTypeEnum {
  @JsonValue(r'reference.updated')
  referencePeriodUpdated(r'reference.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventReferenceUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
