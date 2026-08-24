// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_aborted_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessageAbortedError _$MessageAbortedErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MessageAbortedError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = MessageAbortedError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$MessageAbortedErrorNameEnumEnumMap,
            v,
            unknownValue: MessageAbortedErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MessageAbortedErrorToJson(
  MessageAbortedError instance,
) => <String, dynamic>{
  'name': _$MessageAbortedErrorNameEnumEnumMap[instance.name]!,
  'data': instance.data.toJson(),
};

const _$MessageAbortedErrorNameEnumEnumMap = {
  MessageAbortedErrorNameEnum.messageAbortedError: 'MessageAbortedError',
  MessageAbortedErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
