// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_v2_capabilities.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelV2Capabilities _$ModelV2CapabilitiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelV2Capabilities', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['tools', 'input', 'output']);
      final val = ModelV2Capabilities(
        tools: $checkedConvert('tools', (v) => v as bool),
        input: $checkedConvert(
          'input',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        output: $checkedConvert(
          'output',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ModelV2CapabilitiesToJson(
  ModelV2Capabilities instance,
) => <String, dynamic>{
  'tools': instance.tools,
  'input': instance.input,
  'output': instance.output,
};
