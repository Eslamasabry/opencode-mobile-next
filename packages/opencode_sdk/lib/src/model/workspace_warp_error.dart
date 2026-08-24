//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'workspace_warp_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorkspaceWarpError {
  /// Returns a new [WorkspaceWarpError] instance.
  WorkspaceWarpError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: WorkspaceWarpErrorNameEnum.unknownDefaultOpenApi,
  )
  final WorkspaceWarpErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final MoveSessionErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorkspaceWarpError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory WorkspaceWarpError.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceWarpErrorFromJson(json);

  Map<String, dynamic> toJson() => _$WorkspaceWarpErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WorkspaceWarpErrorNameEnum {
  @JsonValue(r'WorkspaceWarpError')
  workspaceWarpError(r'WorkspaceWarpError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const WorkspaceWarpErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
