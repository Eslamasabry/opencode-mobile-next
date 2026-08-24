// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_auth_remove200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpAuthRemove200Response _$McpAuthRemove200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpAuthRemove200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['success']);
  final val = McpAuthRemove200Response(
    success: $checkedConvert(
      'success',
      (v) => $enumDecode(
        _$McpAuthRemove200ResponseSuccessEnumEnumMap,
        v,
        unknownValue: McpAuthRemove200ResponseSuccessEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$McpAuthRemove200ResponseToJson(
  McpAuthRemove200Response instance,
) => <String, dynamic>{
  'success': _$McpAuthRemove200ResponseSuccessEnumEnumMap[instance.success]!,
};

const _$McpAuthRemove200ResponseSuccessEnumEnumMap = {
  McpAuthRemove200ResponseSuccessEnum.true_: 'true',
  McpAuthRemove200ResponseSuccessEnum.unknownDefaultOpenApi: '11184809',
};
