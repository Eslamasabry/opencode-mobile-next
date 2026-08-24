//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderList200Response {
  /// Returns a new [ProviderList200Response] instance.
  ProviderList200Response({
    required this.all,

    required this.default_,

    required this.connected,
  });

  @JsonKey(name: r'all', required: true, includeIfNull: false)
  final List<Provider> all;

  @JsonKey(name: r'default', required: true, includeIfNull: false)
  final Map<String, String> default_;

  @JsonKey(name: r'connected', required: true, includeIfNull: false)
  final List<String> connected;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderList200Response &&
            runtimeType == other.runtimeType &&
            equals(
              [all, default_, connected],
              [other.all, other.default_, other.connected],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([all, default_, connected]);

  factory ProviderList200Response.fromJson(Map<String, dynamic> json) =>
      _$ProviderList200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
