//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_respond_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionRespondRequest {
  /// Returns a new [PermissionRespondRequest] instance.
  PermissionRespondRequest({required this.response});

  @JsonKey(
    name: r'response',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        PermissionRespondRequestResponseEnum.unknownDefaultOpenApi,
  )
  final PermissionRespondRequestResponseEnum response;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionRespondRequest &&
            runtimeType == other.runtimeType &&
            equals([response], [other.response]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([response]);

  factory PermissionRespondRequest.fromJson(Map<String, dynamic> json) =>
      _$PermissionRespondRequestFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionRespondRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionRespondRequestResponseEnum {
  @JsonValue(r'once')
  once(r'once'),
  @JsonValue(r'always')
  always(r'always'),
  @JsonValue(r'reject')
  reject(r'reject'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionRespondRequestResponseEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
