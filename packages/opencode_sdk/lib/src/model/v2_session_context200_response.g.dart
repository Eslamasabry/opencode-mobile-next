// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_context200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionContext200Response _$V2SessionContext200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionContext200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionContext200Response(
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>).map(SessionMessage.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionContext200ResponseToJson(
  V2SessionContext200Response instance,
) => <String, dynamic>{'data': instance.data.map((e) => e.toJson()).toList()};
