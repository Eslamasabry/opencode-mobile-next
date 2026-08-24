// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_reply_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionReplyRequest _$PermissionReplyRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionReplyRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['reply']);
  final val = PermissionReplyRequest(
    reply: $checkedConvert(
      'reply',
      (v) => $enumDecode(
        _$PermissionReplyRequestReplyEnumEnumMap,
        v,
        unknownValue: PermissionReplyRequestReplyEnum.unknownDefaultOpenApi,
      ),
    ),
    message: $checkedConvert('message', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$PermissionReplyRequestToJson(
  PermissionReplyRequest instance,
) => <String, dynamic>{
  'reply': _$PermissionReplyRequestReplyEnumEnumMap[instance.reply]!,
  'message': ?instance.message,
};

const _$PermissionReplyRequestReplyEnumEnumMap = {
  PermissionReplyRequestReplyEnum.once: 'once',
  PermissionReplyRequestReplyEnum.always: 'always',
  PermissionReplyRequestReplyEnum.reject: 'reject',
  PermissionReplyRequestReplyEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
