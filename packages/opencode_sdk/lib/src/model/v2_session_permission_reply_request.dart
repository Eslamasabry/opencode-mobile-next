//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_reply.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_permission_reply_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPermissionReplyRequest {
  /// Returns a new [V2SessionPermissionReplyRequest] instance.
  V2SessionPermissionReplyRequest({required this.reply, this.message});

  @JsonKey(
    name: r'reply',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionV2Reply.unknownDefaultOpenApi,
  )
  final PermissionV2Reply reply;

  @JsonKey(name: r'message', required: false, includeIfNull: false)
  final String? message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPermissionReplyRequest &&
            runtimeType == other.runtimeType &&
            equals([reply, message], [other.reply, other.message]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([reply, message]);

  factory V2SessionPermissionReplyRequest.fromJson(Map<String, dynamic> json) =>
      _$V2SessionPermissionReplyRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2SessionPermissionReplyRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
