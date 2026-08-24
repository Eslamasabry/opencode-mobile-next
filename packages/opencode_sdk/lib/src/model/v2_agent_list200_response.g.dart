// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_agent_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2AgentList200Response _$V2AgentList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2AgentList200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2AgentList200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => AgentV2Info.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2AgentList200ResponseToJson(
  V2AgentList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
