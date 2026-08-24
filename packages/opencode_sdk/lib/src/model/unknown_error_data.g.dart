// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'unknown_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UnknownErrorData _$UnknownErrorDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('UnknownErrorData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['message']);
      final val = UnknownErrorData(
        message: $checkedConvert('message', (v) => v as String),
        ref: $checkedConvert('ref', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$UnknownErrorDataToJson(UnknownErrorData instance) =>
    <String, dynamic>{'message': instance.message, 'ref': ?instance.ref};
