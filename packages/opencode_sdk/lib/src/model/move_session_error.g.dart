// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'move_session_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MoveSessionError _$MoveSessionErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MoveSessionError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = MoveSessionError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$MoveSessionErrorNameEnumEnumMap,
            v,
            unknownValue: MoveSessionErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MoveSessionErrorToJson(MoveSessionError instance) =>
    <String, dynamic>{
      'name': _$MoveSessionErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$MoveSessionErrorNameEnumEnumMap = {
  MoveSessionErrorNameEnum.moveSessionError: 'MoveSessionError',
  MoveSessionErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
