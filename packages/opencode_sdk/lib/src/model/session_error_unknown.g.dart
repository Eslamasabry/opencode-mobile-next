// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_error_unknown.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionErrorUnknown _$SessionErrorUnknownFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionErrorUnknown', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'message']);
      final val = SessionErrorUnknown(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionErrorUnknownTypeEnumEnumMap,
            v,
            unknownValue: SessionErrorUnknownTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SessionErrorUnknownToJson(
  SessionErrorUnknown instance,
) => <String, dynamic>{
  'type': _$SessionErrorUnknownTypeEnumEnumMap[instance.type]!,
  'message': instance.message,
};

const _$SessionErrorUnknownTypeEnumEnumMap = {
  SessionErrorUnknownTypeEnum.unknown: 'unknown',
  SessionErrorUnknownTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
