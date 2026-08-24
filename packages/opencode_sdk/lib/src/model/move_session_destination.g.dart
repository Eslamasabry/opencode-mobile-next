// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'move_session_destination.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MoveSessionDestination _$MoveSessionDestinationFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('MoveSessionDestination', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['directory']);
  final val = MoveSessionDestination(
    directory: $checkedConvert('directory', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$MoveSessionDestinationToJson(
  MoveSessionDestination instance,
) => <String, dynamic>{'directory': instance.directory};
