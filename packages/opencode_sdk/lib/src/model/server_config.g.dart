// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'server_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ServerConfig _$ServerConfigFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ServerConfig', json, ($checkedConvert) {
      final val = ServerConfig(
        port: $checkedConvert('port', (v) => (v as num?)?.toInt()),
        hostname: $checkedConvert('hostname', (v) => v as String?),
        mdns: $checkedConvert('mdns', (v) => v as bool?),
        mdnsDomain: $checkedConvert('mdnsDomain', (v) => v as String?),
        cors: $checkedConvert(
          'cors',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ServerConfigToJson(ServerConfig instance) =>
    <String, dynamic>{
      'port': ?instance.port,
      'hostname': ?instance.hostname,
      'mdns': ?instance.mdns,
      'mdnsDomain': ?instance.mdnsDomain,
      'cors': ?instance.cors,
    };
