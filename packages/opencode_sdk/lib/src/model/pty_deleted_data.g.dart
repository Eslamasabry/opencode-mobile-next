// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_deleted_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyDeletedData _$PtyDeletedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyDeletedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id']);
      final val = PtyDeletedData(id: $checkedConvert('id', (v) => v as String));
      return val;
    });

Map<String, dynamic> _$PtyDeletedDataToJson(PtyDeletedData instance) =>
    <String, dynamic>{'id': instance.id};
