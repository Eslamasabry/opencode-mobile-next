//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_lsp_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventLspUpdated {
  /// Returns a new [EventLspUpdated] instance.
  EventLspUpdated({
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
    unknownEnumValue: EventLspUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventLspUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final Object properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventLspUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventLspUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventLspUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventLspUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventLspUpdatedTypeEnum {
  @JsonValue(r'lsp.updated')
  lspPeriodUpdated(r'lsp.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventLspUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
