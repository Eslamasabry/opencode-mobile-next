//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_global_disposed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventGlobalDisposed {
  /// Returns a new [EventGlobalDisposed] instance.
  EventGlobalDisposed({
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
    unknownEnumValue: EventGlobalDisposedTypeEnum.unknownDefaultOpenApi,
  )
  final EventGlobalDisposedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventGlobalDisposed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventGlobalDisposed.fromJson(Map<String, dynamic> json) =>
      _$EventGlobalDisposedFromJson(json);

  Map<String, dynamic> toJson() => _$EventGlobalDisposedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventGlobalDisposedTypeEnum {
  @JsonValue(r'global.disposed')
  globalPeriodDisposed(r'global.disposed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventGlobalDisposedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
