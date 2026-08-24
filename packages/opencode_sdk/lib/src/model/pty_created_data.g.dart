// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_created_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyCreatedData _$PtyCreatedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyCreatedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['info']);
      final val = PtyCreatedData(
        info: $checkedConvert(
          'info',
          (v) => Pty.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PtyCreatedDataToJson(PtyCreatedData instance) =>
    <String, dynamic>{'info': instance.info.toJson()};
