// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'not_found_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

NotFoundErrorData _$NotFoundErrorDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('NotFoundErrorData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['message']);
      final val = NotFoundErrorData(
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$NotFoundErrorDataToJson(NotFoundErrorData instance) =>
    <String, dynamic>{'message': instance.message};
