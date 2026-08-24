//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_active.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_active200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionActive200Response {
  /// Returns a new [V2SessionActive200Response] instance.
  V2SessionActive200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final Map<String, SessionActive> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionActive200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionActive200Response.fromJson(Map<String, dynamic> json) =>
      _$V2SessionActive200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionActive200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
