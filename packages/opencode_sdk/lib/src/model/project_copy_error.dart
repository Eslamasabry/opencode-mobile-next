//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/project_copy_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_copy_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectCopyError {
  /// Returns a new [ProjectCopyError] instance.
  ProjectCopyError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProjectCopyErrorNameEnum.unknownDefaultOpenApi,
  )
  final ProjectCopyErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final ProjectCopyErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectCopyError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory ProjectCopyError.fromJson(Map<String, dynamic> json) =>
      _$ProjectCopyErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectCopyErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProjectCopyErrorNameEnum {
  @JsonValue(r'ProjectCopyError')
  projectCopyError(r'ProjectCopyError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProjectCopyErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
