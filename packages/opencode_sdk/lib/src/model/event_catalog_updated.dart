//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_catalog_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventCatalogUpdated {
  /// Returns a new [EventCatalogUpdated] instance.
  EventCatalogUpdated({
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
    unknownEnumValue: EventCatalogUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventCatalogUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventCatalogUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventCatalogUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventCatalogUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventCatalogUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventCatalogUpdatedTypeEnum {
  @JsonValue(r'catalog.updated')
  catalogPeriodUpdated(r'catalog.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventCatalogUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
