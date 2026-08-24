//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_aisdk.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAISDK {
  /// Returns a new [ProviderAISDK] instance.
  ProviderAISDK({
    required this.type,

    required this.package,

    this.url,

    this.settings,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderAISDKTypeEnum.unknownDefaultOpenApi,
  )
  final ProviderAISDKTypeEnum type;

  @JsonKey(name: r'package', required: true, includeIfNull: false)
  final String package;

  @JsonKey(name: r'url', required: false, includeIfNull: false)
  final String? url;

  @JsonKey(name: r'settings', required: false, includeIfNull: false)
  final Object? settings;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAISDK &&
            runtimeType == other.runtimeType &&
            equals(
              [type, package, url, settings],
              [other.type, other.package, other.url, other.settings],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, package, url, settings]);

  factory ProviderAISDK.fromJson(Map<String, dynamic> json) =>
      _$ProviderAISDKFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAISDKToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderAISDKTypeEnum {
  @JsonValue(r'aisdk')
  aisdk(r'aisdk'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderAISDKTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
