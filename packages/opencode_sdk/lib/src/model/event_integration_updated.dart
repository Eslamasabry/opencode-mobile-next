//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_integration_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventIntegrationUpdated {
  /// Returns a new [EventIntegrationUpdated] instance.
  EventIntegrationUpdated({
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
    unknownEnumValue: EventIntegrationUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventIntegrationUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventIntegrationUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventIntegrationUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventIntegrationUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventIntegrationUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventIntegrationUpdatedTypeEnum {
  @JsonValue(r'integration.updated')
  integrationPeriodUpdated(r'integration.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventIntegrationUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
