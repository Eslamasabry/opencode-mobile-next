// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_list_item.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolListItem _$ToolListItemFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolListItem', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'description', 'parameters']);
      final val = ToolListItem(
        id: $checkedConvert('id', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String),
        parameters: $checkedConvert('parameters', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$ToolListItemToJson(ToolListItem instance) =>
    <String, dynamic>{
      'id': instance.id,
      'description': instance.description,
      'parameters': instance.parameters,
    };
