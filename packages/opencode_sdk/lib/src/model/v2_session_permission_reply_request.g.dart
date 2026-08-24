// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_permission_reply_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPermissionReplyRequest _$V2SessionPermissionReplyRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionPermissionReplyRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['reply']);
  final val = V2SessionPermissionReplyRequest(
    reply: $checkedConvert(
      'reply',
      (v) => $enumDecode(
        _$PermissionV2ReplyEnumMap,
        v,
        unknownValue: PermissionV2Reply.unknownDefaultOpenApi,
      ),
    ),
    message: $checkedConvert('message', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$V2SessionPermissionReplyRequestToJson(
  V2SessionPermissionReplyRequest instance,
) => <String, dynamic>{
  'reply': _$PermissionV2ReplyEnumMap[instance.reply]!,
  'message': ?instance.message,
};

const _$PermissionV2ReplyEnumMap = {
  PermissionV2Reply.once: 'once',
  PermissionV2Reply.always: 'always',
  PermissionV2Reply.reject: 'reject',
  PermissionV2Reply.unknownDefaultOpenApi: 'unknown_default_open_api',
};
