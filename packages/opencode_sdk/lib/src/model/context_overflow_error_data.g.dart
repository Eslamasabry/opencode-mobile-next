// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'context_overflow_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ContextOverflowErrorData _$ContextOverflowErrorDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ContextOverflowErrorData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['message']);
  final val = ContextOverflowErrorData(
    message: $checkedConvert('message', (v) => v as String),
    responseBody: $checkedConvert('responseBody', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$ContextOverflowErrorDataToJson(
  ContextOverflowErrorData instance,
) => <String, dynamic>{
  'message': instance.message,
  'responseBody': ?instance.responseBody,
};
