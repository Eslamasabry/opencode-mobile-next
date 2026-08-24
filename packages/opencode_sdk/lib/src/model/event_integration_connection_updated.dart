//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_connection_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_integration_connection_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventIntegrationConnectionUpdated {
  /// Returns a new [EventIntegrationConnectionUpdated] instance.
  EventIntegrationConnectionUpdated({
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
        EventIntegrationConnectionUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventIntegrationConnectionUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final IntegrationConnectionUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventIntegrationConnectionUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventIntegrationConnectionUpdated.fromJson(
    Map<String, dynamic> json,
  ) => _$EventIntegrationConnectionUpdatedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventIntegrationConnectionUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventIntegrationConnectionUpdatedTypeEnum {
  @JsonValue(r'integration.connection.updated')
  integrationPeriodConnectionPeriodUpdated(r'integration.connection.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventIntegrationConnectionUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
