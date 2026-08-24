// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_request_tool.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionRequestTool _$PermissionRequestToolFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionRequestTool', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['messageID', 'callID']);
  final val = PermissionRequestTool(
    messageID: $checkedConvert('messageID', (v) => v as String),
    callID: $checkedConvert('callID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$PermissionRequestToolToJson(
  PermissionRequestTool instance,
) => <String, dynamic>{
  'messageID': instance.messageID,
  'callID': instance.callID,
};
