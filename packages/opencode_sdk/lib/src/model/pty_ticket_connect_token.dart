//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_ticket_connect_token.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyTicketConnectToken {
  /// Returns a new [PtyTicketConnectToken] instance.
  PtyTicketConnectToken({required this.ticket, required this.expiresIn});

  @JsonKey(name: r'ticket', required: true, includeIfNull: false)
  final String ticket;

  @JsonKey(name: r'expires_in', required: true, includeIfNull: false)
  final int expiresIn;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyTicketConnectToken &&
            runtimeType == other.runtimeType &&
            equals([ticket, expiresIn], [other.ticket, other.expiresIn]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([ticket, expiresIn]);

  factory PtyTicketConnectToken.fromJson(Map<String, dynamic> json) =>
      _$PtyTicketConnectTokenFromJson(json);

  Map<String, dynamic> toJson() => _$PtyTicketConnectTokenToJson(this);

  String toString() {
    return toJson().toString();
  }
}
