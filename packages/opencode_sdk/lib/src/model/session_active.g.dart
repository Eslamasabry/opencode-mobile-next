// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_active.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionActive _$SessionActiveFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionActive', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type']);
      final val = SessionActive(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionActiveTypeEnumEnumMap,
            v,
            unknownValue: SessionActiveTypeEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionActiveToJson(SessionActive instance) =>
    <String, dynamic>{'type': _$SessionActiveTypeEnumEnumMap[instance.type]!};

const _$SessionActiveTypeEnumEnumMap = {
  SessionActiveTypeEnum.running: 'running',
  SessionActiveTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
