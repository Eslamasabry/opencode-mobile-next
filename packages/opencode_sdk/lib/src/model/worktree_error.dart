//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'worktree_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorktreeError {
  /// Returns a new [WorktreeError] instance.
  WorktreeError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: WorktreeErrorNameEnum.unknownDefaultOpenApi,
  )
  final WorktreeErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final MoveSessionErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorktreeError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory WorktreeError.fromJson(Map<String, dynamic> json) =>
      _$WorktreeErrorFromJson(json);

  Map<String, dynamic> toJson() => _$WorktreeErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WorktreeErrorNameEnum {
  @JsonValue(r'WorktreeNotGitError')
  worktreeNotGitError(r'WorktreeNotGitError'),
  @JsonValue(r'WorktreeNameGenerationFailedError')
  worktreeNameGenerationFailedError(r'WorktreeNameGenerationFailedError'),
  @JsonValue(r'WorktreeCreateFailedError')
  worktreeCreateFailedError(r'WorktreeCreateFailedError'),
  @JsonValue(r'WorktreeStartCommandFailedError')
  worktreeStartCommandFailedError(r'WorktreeStartCommandFailedError'),
  @JsonValue(r'WorktreeRemoveFailedError')
  worktreeRemoveFailedError(r'WorktreeRemoveFailedError'),
  @JsonValue(r'WorktreeResetFailedError')
  worktreeResetFailedError(r'WorktreeResetFailedError'),
  @JsonValue(r'WorktreeListFailedError')
  worktreeListFailedError(r'WorktreeListFailedError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const WorktreeErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
