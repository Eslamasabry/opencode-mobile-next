// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_exited_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyExitedData _$PtyExitedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyExitedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'exitCode']);
      final val = PtyExitedData(
        id: $checkedConvert('id', (v) => v as String),
        exitCode: $checkedConvert('exitCode', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$PtyExitedDataToJson(PtyExitedData instance) =>
    <String, dynamic>{'id': instance.id, 'exitCode': instance.exitCode};
