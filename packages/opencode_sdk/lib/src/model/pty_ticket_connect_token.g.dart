// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_ticket_connect_token.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyTicketConnectToken _$PtyTicketConnectTokenFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PtyTicketConnectToken', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['ticket', 'expires_in']);
  final val = PtyTicketConnectToken(
    ticket: $checkedConvert('ticket', (v) => v as String),
    expiresIn: $checkedConvert('expires_in', (v) => (v as num).toInt()),
  );
  return val;
}, fieldKeyMap: const {'expiresIn': 'expires_in'});

Map<String, dynamic> _$PtyTicketConnectTokenToJson(
  PtyTicketConnectToken instance,
) => <String, dynamic>{
  'ticket': instance.ticket,
  'expires_in': instance.expiresIn,
};
