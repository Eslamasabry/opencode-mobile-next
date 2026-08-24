//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_providers200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigProviders200Response {
  /// Returns a new [ConfigProviders200Response] instance.
  ConfigProviders200Response({required this.providers, required this.default_});

  @JsonKey(name: r'providers', required: true, includeIfNull: false)
  final List<Provider> providers;

  @JsonKey(name: r'default', required: true, includeIfNull: false)
  final Map<String, String> default_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigProviders200Response &&
            runtimeType == other.runtimeType &&
            equals([providers, default_], [other.providers, other.default_]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([providers, default_]);

  factory ConfigProviders200Response.fromJson(Map<String, dynamic> json) =>
      _$ConfigProviders200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigProviders200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
