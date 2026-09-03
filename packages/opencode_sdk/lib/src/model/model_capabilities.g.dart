// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_capabilities.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCapabilities _$ModelCapabilitiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelCapabilities', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'temperature',
          'reasoning',
          'attachment',
          'toolcall',
          'input',
          'output',
          'interleaved',
        ],
      );
      final val = ModelCapabilities(
        temperature: $checkedConvert('temperature', (v) => v as bool),
        reasoning: $checkedConvert('reasoning', (v) => v as bool),
        attachment: $checkedConvert('attachment', (v) => v as bool),
        toolcall: $checkedConvert('toolcall', (v) => v as bool),
        input: $checkedConvert(
          'input',
          (v) => ModelCapabilitiesInput.fromJson(v as Map<String, dynamic>),
        ),
        output: $checkedConvert(
          'output',
          (v) => ModelCapabilitiesInput.fromJson(v as Map<String, dynamic>),
        ),
        interleaved: $checkedConvert(
          'interleaved',
          (v) => OpencodeSdkRawUnion016.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ModelCapabilitiesToJson(ModelCapabilities instance) =>
    <String, dynamic>{
      'temperature': instance.temperature,
      'reasoning': instance.reasoning,
      'attachment': instance.attachment,
      'toolcall': instance.toolcall,
      'input': instance.input.toJson(),
      'output': instance.output.toJson(),
      'interleaved': instance.interleaved.toJson(),
    };
