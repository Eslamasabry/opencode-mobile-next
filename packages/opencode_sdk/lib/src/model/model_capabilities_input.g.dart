// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_capabilities_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCapabilitiesInput _$ModelCapabilitiesInputFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ModelCapabilitiesInput', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['text', 'audio', 'image', 'video', 'pdf'],
  );
  final val = ModelCapabilitiesInput(
    text: $checkedConvert('text', (v) => v as bool),
    audio: $checkedConvert('audio', (v) => v as bool),
    image: $checkedConvert('image', (v) => v as bool),
    video: $checkedConvert('video', (v) => v as bool),
    pdf: $checkedConvert('pdf', (v) => v as bool),
  );
  return val;
});

Map<String, dynamic> _$ModelCapabilitiesInputToJson(
  ModelCapabilitiesInput instance,
) => <String, dynamic>{
  'text': instance.text,
  'audio': instance.audio,
  'image': instance.image,
  'video': instance.video,
  'pdf': instance.pdf,
};
