// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_workspace_warp_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalWorkspaceWarpRequest _$ExperimentalWorkspaceWarpRequestFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('ExperimentalWorkspaceWarpRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'sessionID']);
      final val = ExperimentalWorkspaceWarpRequest(
        id: $checkedConvert('id', (v) => v as String?),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        copyChanges: $checkedConvert('copyChanges', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$ExperimentalWorkspaceWarpRequestToJson(
  ExperimentalWorkspaceWarpRequest instance,
) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'copyChanges': ?instance.copyChanges,
};
