//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider_v2_info.dart';
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_provider_get200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2ProviderGet200Response {
  /// Returns a new [V2ProviderGet200Response] instance.
  V2ProviderGet200Response({required this.location, required this.data});

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationInfo location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final ProviderV2Info data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2ProviderGet200Response &&
            runtimeType == other.runtimeType &&
            equals([location, data], [other.location, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([location, data]);

  factory V2ProviderGet200Response.fromJson(Map<String, dynamic> json) =>
      _$V2ProviderGet200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2ProviderGet200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
