// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_show_toast_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiShowToastRequest _$TuiShowToastRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TuiShowToastRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['message', 'variant']);
      final val = TuiShowToastRequest(
        title: $checkedConvert('title', (v) => v as String?),
        message: $checkedConvert('message', (v) => v as String),
        variant: $checkedConvert(
          'variant',
          (v) => $enumDecode(
            _$TuiShowToastRequestVariantEnumEnumMap,
            v,
            unknownValue: TuiShowToastRequestVariantEnum.unknownDefaultOpenApi,
          ),
        ),
        duration: $checkedConvert('duration', (v) => (v as num?)?.toInt()),
      );
      return val;
    });

Map<String, dynamic> _$TuiShowToastRequestToJson(
  TuiShowToastRequest instance,
) => <String, dynamic>{
  'title': ?instance.title,
  'message': instance.message,
  'variant': _$TuiShowToastRequestVariantEnumEnumMap[instance.variant]!,
  'duration': ?instance.duration,
};

const _$TuiShowToastRequestVariantEnumEnumMap = {
  TuiShowToastRequestVariantEnum.info: 'info',
  TuiShowToastRequestVariantEnum.success: 'success',
  TuiShowToastRequestVariantEnum.warning: 'warning',
  TuiShowToastRequestVariantEnum.error: 'error',
  TuiShowToastRequestVariantEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
