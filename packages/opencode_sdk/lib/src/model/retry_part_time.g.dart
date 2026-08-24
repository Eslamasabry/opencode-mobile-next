// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'retry_part_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RetryPartTime _$RetryPartTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('RetryPartTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['created']);
      final val = RetryPartTime(
        created: $checkedConvert('created', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$RetryPartTimeToJson(RetryPartTime instance) =>
    <String, dynamic>{'created': instance.created};
