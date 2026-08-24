// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_replied_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionRepliedData _$PermissionRepliedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionRepliedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'requestID', 'reply']);
  final val = PermissionRepliedData(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    requestID: $checkedConvert('requestID', (v) => v as String),
    reply: $checkedConvert(
      'reply',
      (v) => $enumDecode(
        _$PermissionRepliedDataReplyEnumEnumMap,
        v,
        unknownValue: PermissionRepliedDataReplyEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$PermissionRepliedDataToJson(
  PermissionRepliedData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'requestID': instance.requestID,
  'reply': _$PermissionRepliedDataReplyEnumEnumMap[instance.reply]!,
};

const _$PermissionRepliedDataReplyEnumEnumMap = {
  PermissionRepliedDataReplyEnum.once: 'once',
  PermissionRepliedDataReplyEnum.always: 'always',
  PermissionRepliedDataReplyEnum.reject: 'reject',
  PermissionRepliedDataReplyEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
