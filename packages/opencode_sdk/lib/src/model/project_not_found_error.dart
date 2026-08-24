//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectNotFoundError {
  /// Returns a new [ProjectNotFoundError] instance.
  ProjectNotFoundError({
    required this.tag,

    required this.projectID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProjectNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final ProjectNotFoundErrorTagEnum tag;

  @JsonKey(name: r'projectID', required: true, includeIfNull: false)
  final String projectID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, projectID, message],
              [other.tag, other.projectID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, projectID, message]);

  factory ProjectNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$ProjectNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProjectNotFoundErrorTagEnum {
  @JsonValue(r'ProjectNotFoundError')
  projectNotFoundError(r'ProjectNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProjectNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
