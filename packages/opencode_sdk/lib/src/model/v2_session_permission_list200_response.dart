//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_permission_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPermissionList200Response {
  /// Returns a new [V2SessionPermissionList200Response] instance.
  V2SessionPermissionList200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<PermissionV2Request> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPermissionList200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionPermissionList200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2SessionPermissionList200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2SessionPermissionList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
