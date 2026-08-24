// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_respond_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionRespondRequest _$PermissionRespondRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionRespondRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['response']);
  final val = PermissionRespondRequest(
    response: $checkedConvert(
      'response',
      (v) => $enumDecode(
        _$PermissionRespondRequestResponseEnumEnumMap,
        v,
        unknownValue:
            PermissionRespondRequestResponseEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$PermissionRespondRequestToJson(
  PermissionRespondRequest instance,
) => <String, dynamic>{
  'response': _$PermissionRespondRequestResponseEnumEnumMap[instance.response]!,
};

const _$PermissionRespondRequestResponseEnumEnumMap = {
  PermissionRespondRequestResponseEnum.once: 'once',
  PermissionRespondRequestResponseEnum.always: 'always',
  PermissionRespondRequestResponseEnum.reject: 'reject',
  PermissionRespondRequestResponseEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
