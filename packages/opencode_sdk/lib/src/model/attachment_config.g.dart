// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'attachment_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AttachmentConfig _$AttachmentConfigFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AttachmentConfig', json, ($checkedConvert) {
      final val = AttachmentConfig(
        image: $checkedConvert(
          'image',
          (v) => v == null
              ? null
              : ImageAttachmentConfig.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$AttachmentConfigToJson(AttachmentConfig instance) =>
    <String, dynamic>{'image': ?instance.image?.toJson()};
