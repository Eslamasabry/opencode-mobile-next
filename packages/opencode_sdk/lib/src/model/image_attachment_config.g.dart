// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'image_attachment_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ImageAttachmentConfig _$ImageAttachmentConfigFromJson(
  Map<String, dynamic> json,
) => $checkedCreate(
  'ImageAttachmentConfig',
  json,
  ($checkedConvert) {
    final val = ImageAttachmentConfig(
      autoResize: $checkedConvert('auto_resize', (v) => v as bool?),
      maxWidth: $checkedConvert('max_width', (v) => (v as num?)?.toInt()),
      maxHeight: $checkedConvert('max_height', (v) => (v as num?)?.toInt()),
      maxBase64Bytes: $checkedConvert(
        'max_base64_bytes',
        (v) => (v as num?)?.toInt(),
      ),
    );
    return val;
  },
  fieldKeyMap: const {
    'autoResize': 'auto_resize',
    'maxWidth': 'max_width',
    'maxHeight': 'max_height',
    'maxBase64Bytes': 'max_base64_bytes',
  },
);

Map<String, dynamic> _$ImageAttachmentConfigToJson(
  ImageAttachmentConfig instance,
) => <String, dynamic>{
  'auto_resize': ?instance.autoResize,
  'max_width': ?instance.maxWidth,
  'max_height': ?instance.maxHeight,
  'max_base64_bytes': ?instance.maxBase64Bytes,
};
