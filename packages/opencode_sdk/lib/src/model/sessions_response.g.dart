// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sessions_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionsResponse _$SessionsResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionsResponse', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['data', 'cursor']);
      final val = SessionsResponse(
        data: $checkedConvert(
          'data',
          (v) => (v as List<dynamic>)
              .map((e) => SessionV2Info.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
        cursor: $checkedConvert(
          'cursor',
          (v) => SessionsResponseCursor.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionsResponseToJson(SessionsResponse instance) =>
    <String, dynamic>{
      'data': instance.data.map((e) => e.toJson()).toList(),
      'cursor': instance.cursor.toJson(),
    };
