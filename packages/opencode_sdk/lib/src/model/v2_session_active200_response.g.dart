// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_active200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionActive200Response _$V2SessionActive200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionActive200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionActive200Response(
    data: $checkedConvert(
      'data',
      (v) => (v as Map<String, dynamic>).map(
        (k, e) =>
            MapEntry(k, SessionActive.fromJson(e as Map<String, dynamic>)),
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionActive200ResponseToJson(
  V2SessionActive200Response instance,
) => <String, dynamic>{
  'data': instance.data.map((k, e) => MapEntry(k, e.toJson())),
};
