//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/project_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_project_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventProjectUpdated {
  /// Returns a new [EventProjectUpdated] instance.
  EventProjectUpdated({
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
    unknownEnumValue: EventProjectUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventProjectUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final ProjectUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventProjectUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventProjectUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventProjectUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventProjectUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventProjectUpdatedTypeEnum {
  @JsonValue(r'project.updated')
  projectPeriodUpdated(r'project.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventProjectUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
