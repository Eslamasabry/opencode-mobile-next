// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessageNotFoundError _$MessageNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('MessageNotFoundError', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['_tag', 'sessionID', 'messageID', 'message'],
  );
  final val = MessageNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$MessageNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: MessageNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$MessageNotFoundErrorToJson(
  MessageNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$MessageNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'message': instance.message,
};

const _$MessageNotFoundErrorTagEnumEnumMap = {
  MessageNotFoundErrorTagEnum.messageNotFoundError: 'MessageNotFoundError',
  MessageNotFoundErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
