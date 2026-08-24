// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'content_filter_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ContentFilterError _$ContentFilterErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ContentFilterError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = ContentFilterError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$ContentFilterErrorNameEnumEnumMap,
            v,
            unknownValue: ContentFilterErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ContentFilterErrorToJson(ContentFilterError instance) =>
    <String, dynamic>{
      'name': _$ContentFilterErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$ContentFilterErrorNameEnumEnumMap = {
  ContentFilterErrorNameEnum.contentFilterError: 'ContentFilterError',
  ContentFilterErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
