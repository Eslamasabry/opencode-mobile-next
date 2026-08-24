//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/project_directories_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_project_directories_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventProjectDirectoriesUpdated {
  /// Returns a new [EventProjectDirectoriesUpdated] instance.
  EventProjectDirectoriesUpdated({
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
        EventProjectDirectoriesUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventProjectDirectoriesUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final ProjectDirectoriesUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventProjectDirectoriesUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventProjectDirectoriesUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventProjectDirectoriesUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventProjectDirectoriesUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventProjectDirectoriesUpdatedTypeEnum {
  @JsonValue(r'project.directories.updated')
  projectPeriodDirectoriesPeriodUpdated(r'project.directories.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventProjectDirectoriesUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
