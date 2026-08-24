// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusAnyOf _$SessionStatusAnyOfFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionStatusAnyOf', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type']);
      final val = SessionStatusAnyOf(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionStatusAnyOfTypeEnumEnumMap,
            v,
            unknownValue: SessionStatusAnyOfTypeEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionStatusAnyOfToJson(SessionStatusAnyOf instance) =>
    <String, dynamic>{
      'type': _$SessionStatusAnyOfTypeEnumEnumMap[instance.type]!,
    };

const _$SessionStatusAnyOfTypeEnumEnumMap = {
  SessionStatusAnyOfTypeEnum.idle: 'idle',
  SessionStatusAnyOfTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
