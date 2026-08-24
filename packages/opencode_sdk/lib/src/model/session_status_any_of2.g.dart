// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_any_of2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusAnyOf2 _$SessionStatusAnyOf2FromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionStatusAnyOf2', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type']);
      final val = SessionStatusAnyOf2(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionStatusAnyOf2TypeEnumEnumMap,
            v,
            unknownValue: SessionStatusAnyOf2TypeEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionStatusAnyOf2ToJson(
  SessionStatusAnyOf2 instance,
) => <String, dynamic>{
  'type': _$SessionStatusAnyOf2TypeEnumEnumMap[instance.type]!,
};

const _$SessionStatusAnyOf2TypeEnumEnumMap = {
  SessionStatusAnyOf2TypeEnum.busy: 'busy',
  SessionStatusAnyOf2TypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
