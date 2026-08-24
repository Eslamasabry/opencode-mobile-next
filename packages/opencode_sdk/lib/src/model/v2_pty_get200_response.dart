//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:opencode_sdk/src/model/pty.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_pty_get200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2PtyGet200Response {
  /// Returns a new [V2PtyGet200Response] instance.
  V2PtyGet200Response({required this.location, required this.data});

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationInfo location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final Pty data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2PtyGet200Response &&
            runtimeType == other.runtimeType &&
            equals([location, data], [other.location, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([location, data]);

  factory V2PtyGet200Response.fromJson(Map<String, dynamic> json) =>
      _$V2PtyGet200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2PtyGet200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
