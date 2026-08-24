//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'workspace_create_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorkspaceCreateError {
  /// Returns a new [WorkspaceCreateError] instance.
  WorkspaceCreateError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: WorkspaceCreateErrorNameEnum.unknownDefaultOpenApi,
  )
  final WorkspaceCreateErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final MoveSessionErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorkspaceCreateError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory WorkspaceCreateError.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceCreateErrorFromJson(json);

  Map<String, dynamic> toJson() => _$WorkspaceCreateErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WorkspaceCreateErrorNameEnum {
  @JsonValue(r'WorkspaceCreateError')
  workspaceCreateError(r'WorkspaceCreateError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const WorkspaceCreateErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
