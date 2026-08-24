// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_permission_create200_response_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPermissionCreate200ResponseData
_$V2SessionPermissionCreate200ResponseDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('V2SessionPermissionCreate200ResponseData', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['id', 'effect']);
      final val = V2SessionPermissionCreate200ResponseData(
        id: $checkedConvert('id', (v) => v as String),
        effect: $checkedConvert(
          'effect',
          (v) => $enumDecode(
            _$PermissionV2EffectEnumMap,
            v,
            unknownValue: PermissionV2Effect.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2SessionPermissionCreate200ResponseDataToJson(
  V2SessionPermissionCreate200ResponseData instance,
) => <String, dynamic>{
  'id': instance.id,
  'effect': _$PermissionV2EffectEnumMap[instance.effect]!,
};

const _$PermissionV2EffectEnumMap = {
  PermissionV2Effect.allow: 'allow',
  PermissionV2Effect.deny: 'deny',
  PermissionV2Effect.ask: 'ask',
  PermissionV2Effect.unknownDefaultOpenApi: 'unknown_default_open_api',
};
