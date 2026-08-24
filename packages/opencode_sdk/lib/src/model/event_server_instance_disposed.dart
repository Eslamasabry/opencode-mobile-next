//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/event_server_instance_disposed_properties.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_server_instance_disposed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventServerInstanceDisposed {
  /// Returns a new [EventServerInstanceDisposed] instance.
  EventServerInstanceDisposed({
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
    unknownEnumValue: EventServerInstanceDisposedTypeEnum.unknownDefaultOpenApi,
  )
  final EventServerInstanceDisposedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final EventServerInstanceDisposedProperties properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventServerInstanceDisposed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventServerInstanceDisposed.fromJson(Map<String, dynamic> json) =>
      _$EventServerInstanceDisposedFromJson(json);

  Map<String, dynamic> toJson() => _$EventServerInstanceDisposedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventServerInstanceDisposedTypeEnum {
  @JsonValue(r'server.instance.disposed')
  serverPeriodInstancePeriodDisposed(r'server.instance.disposed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventServerInstanceDisposedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
