//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_server_connected.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventServerConnected {
  /// Returns a new [EventServerConnected] instance.
  EventServerConnected({
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
    unknownEnumValue: EventServerConnectedTypeEnum.unknownDefaultOpenApi,
  )
  final EventServerConnectedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventServerConnected &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventServerConnected.fromJson(Map<String, dynamic> json) =>
      _$EventServerConnectedFromJson(json);

  Map<String, dynamic> toJson() => _$EventServerConnectedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventServerConnectedTypeEnum {
  @JsonValue(r'server.connected')
  serverPeriodConnected(r'server.connected'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventServerConnectedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
