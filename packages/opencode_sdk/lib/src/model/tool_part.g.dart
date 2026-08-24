// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolPart _$ToolPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'callID',
          'tool',
          'state',
        ],
      );
      final val = ToolPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ToolPartTypeEnumEnumMap,
            v,
            unknownValue: ToolPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        callID: $checkedConvert('callID', (v) => v as String),
        tool: $checkedConvert('tool', (v) => v as String),
        state: $checkedConvert('state', (v) => ToolState.fromJson(v)),
        metadata: $checkedConvert('metadata', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$ToolPartToJson(ToolPart instance) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'type': _$ToolPartTypeEnumEnumMap[instance.type]!,
  'callID': instance.callID,
  'tool': instance.tool,
  'state': instance.state.toJson(),
  'metadata': ?instance.metadata,
};

const _$ToolPartTypeEnumEnumMap = {
  ToolPartTypeEnum.tool: 'tool',
  ToolPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
