// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'well_known_auth.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WellKnownAuth _$WellKnownAuthFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WellKnownAuth', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'key', 'token']);
      final val = WellKnownAuth(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$WellKnownAuthTypeEnumEnumMap,
            v,
            unknownValue: WellKnownAuthTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        key: $checkedConvert('key', (v) => v as String),
        token: $checkedConvert('token', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$WellKnownAuthToJson(WellKnownAuth instance) =>
    <String, dynamic>{
      'type': _$WellKnownAuthTypeEnumEnumMap[instance.type]!,
      'key': instance.key,
      'token': instance.token,
    };

const _$WellKnownAuthTypeEnumEnumMap = {
  WellKnownAuthTypeEnum.wellknown: 'wellknown',
  WellKnownAuthTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
