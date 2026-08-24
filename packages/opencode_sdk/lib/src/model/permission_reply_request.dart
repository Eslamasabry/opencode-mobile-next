//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_reply_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionReplyRequest {
  /// Returns a new [PermissionReplyRequest] instance.
  PermissionReplyRequest({required this.reply, this.message});

  @JsonKey(
    name: r'reply',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionReplyRequestReplyEnum.unknownDefaultOpenApi,
  )
  final PermissionReplyRequestReplyEnum reply;

  @JsonKey(name: r'message', required: false, includeIfNull: false)
  final String? message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionReplyRequest &&
            runtimeType == other.runtimeType &&
            equals([reply, message], [other.reply, other.message]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([reply, message]);

  factory PermissionReplyRequest.fromJson(Map<String, dynamic> json) =>
      _$PermissionReplyRequestFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionReplyRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionReplyRequestReplyEnum {
  @JsonValue(r'once')
  once(r'once'),
  @JsonValue(r'always')
  always(r'always'),
  @JsonValue(r'reject')
  reject(r'reject'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionReplyRequestReplyEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
