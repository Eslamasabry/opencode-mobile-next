// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_enterprise.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigEnterprise _$ConfigEnterpriseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConfigEnterprise', json, ($checkedConvert) {
      final val = ConfigEnterprise(
        url: $checkedConvert('url', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$ConfigEnterpriseToJson(ConfigEnterprise instance) =>
    <String, dynamic>{'url': ?instance.url};
