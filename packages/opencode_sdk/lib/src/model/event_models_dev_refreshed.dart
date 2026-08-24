//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_models_dev_refreshed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventModelsDevRefreshed {
  /// Returns a new [EventModelsDevRefreshed] instance.
  EventModelsDevRefreshed({
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
    unknownEnumValue: EventModelsDevRefreshedTypeEnum.unknownDefaultOpenApi,
  )
  final EventModelsDevRefreshedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventModelsDevRefreshed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventModelsDevRefreshed.fromJson(Map<String, dynamic> json) =>
      _$EventModelsDevRefreshedFromJson(json);

  Map<String, dynamic> toJson() => _$EventModelsDevRefreshedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventModelsDevRefreshedTypeEnum {
  @JsonValue(r'models-dev.refreshed')
  modelsDevPeriodRefreshed(r'models-dev.refreshed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventModelsDevRefreshedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
