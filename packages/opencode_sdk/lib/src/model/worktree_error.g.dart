// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeError _$WorktreeErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = WorktreeError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$WorktreeErrorNameEnumEnumMap,
            v,
            unknownValue: WorktreeErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeErrorToJson(WorktreeError instance) =>
    <String, dynamic>{
      'name': _$WorktreeErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$WorktreeErrorNameEnumEnumMap = {
  WorktreeErrorNameEnum.worktreeNotGitError: 'WorktreeNotGitError',
  WorktreeErrorNameEnum.worktreeNameGenerationFailedError:
      'WorktreeNameGenerationFailedError',
  WorktreeErrorNameEnum.worktreeCreateFailedError: 'WorktreeCreateFailedError',
  WorktreeErrorNameEnum.worktreeStartCommandFailedError:
      'WorktreeStartCommandFailedError',
  WorktreeErrorNameEnum.worktreeRemoveFailedError: 'WorktreeRemoveFailedError',
  WorktreeErrorNameEnum.worktreeResetFailedError: 'WorktreeResetFailedError',
  WorktreeErrorNameEnum.worktreeListFailedError: 'WorktreeListFailedError',
  WorktreeErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
