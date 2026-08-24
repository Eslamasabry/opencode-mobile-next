// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'lsp_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LSPStatus _$LSPStatusFromJson(Map<String, dynamic> json) =>
    $checkedCreate('LSPStatus', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'name', 'root', 'status']);
      final val = LSPStatus(
        id: $checkedConvert('id', (v) => v as String),
        name: $checkedConvert('name', (v) => v as String),
        root: $checkedConvert('root', (v) => v as String),
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$LSPStatusStatusEnumEnumMap,
            v,
            unknownValue: LSPStatusStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$LSPStatusToJson(LSPStatus instance) => <String, dynamic>{
  'id': instance.id,
  'name': instance.name,
  'root': instance.root,
  'status': _$LSPStatusStatusEnumEnumMap[instance.status]!,
};

const _$LSPStatusStatusEnumEnumMap = {
  LSPStatusStatusEnum.connected: 'connected',
  LSPStatusStatusEnum.error: 'error',
  LSPStatusStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
