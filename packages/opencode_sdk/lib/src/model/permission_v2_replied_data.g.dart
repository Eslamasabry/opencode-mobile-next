// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_v2_replied_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionV2RepliedData _$PermissionV2RepliedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionV2RepliedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'requestID', 'reply']);
  final val = PermissionV2RepliedData(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    requestID: $checkedConvert('requestID', (v) => v as String),
    reply: $checkedConvert(
      'reply',
      (v) => $enumDecode(
        _$PermissionV2ReplyEnumMap,
        v,
        unknownValue: PermissionV2Reply.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$PermissionV2RepliedDataToJson(
  PermissionV2RepliedData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'requestID': instance.requestID,
  'reply': _$PermissionV2ReplyEnumMap[instance.reply]!,
};

const _$PermissionV2ReplyEnumMap = {
  PermissionV2Reply.once: 'once',
  PermissionV2Reply.always: 'always',
  PermissionV2Reply.reject: 'reject',
  PermissionV2Reply.unknownDefaultOpenApi: 'unknown_default_open_api',
};
