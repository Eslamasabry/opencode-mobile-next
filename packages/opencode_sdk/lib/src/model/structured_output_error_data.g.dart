// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'structured_output_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

StructuredOutputErrorData _$StructuredOutputErrorDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('StructuredOutputErrorData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['message', 'retries']);
  final val = StructuredOutputErrorData(
    message: $checkedConvert('message', (v) => v as String),
    retries: $checkedConvert('retries', (v) => (v as num).toInt()),
  );
  return val;
});

Map<String, dynamic> _$StructuredOutputErrorDataToJson(
  StructuredOutputErrorData instance,
) => <String, dynamic>{
  'message': instance.message,
  'retries': instance.retries,
};
