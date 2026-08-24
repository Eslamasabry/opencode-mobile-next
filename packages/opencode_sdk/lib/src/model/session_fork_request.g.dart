// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_fork_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionForkRequest _$SessionForkRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionForkRequest', json, ($checkedConvert) {
      final val = SessionForkRequest(
        messageID: $checkedConvert('messageID', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$SessionForkRequestToJson(SessionForkRequest instance) =>
    <String, dynamic>{'messageID': ?instance.messageID};
