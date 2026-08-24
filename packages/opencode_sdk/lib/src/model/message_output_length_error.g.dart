// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_output_length_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessageOutputLengthError _$MessageOutputLengthErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('MessageOutputLengthError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['name', 'data']);
  final val = MessageOutputLengthError(
    name: $checkedConvert(
      'name',
      (v) => $enumDecode(
        _$MessageOutputLengthErrorNameEnumEnumMap,
        v,
        unknownValue: MessageOutputLengthErrorNameEnum.unknownDefaultOpenApi,
      ),
    ),
    data: $checkedConvert('data', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$MessageOutputLengthErrorToJson(
  MessageOutputLengthError instance,
) => <String, dynamic>{
  'name': _$MessageOutputLengthErrorNameEnumEnumMap[instance.name]!,
  'data': instance.data,
};

const _$MessageOutputLengthErrorNameEnumEnumMap = {
  MessageOutputLengthErrorNameEnum.messageOutputLengthError:
      'MessageOutputLengthError',
  MessageOutputLengthErrorNameEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
