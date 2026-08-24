//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union004.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union005.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_options.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigOptions {
  /// Returns a new [ProviderConfigOptions] instance.
  ProviderConfigOptions({
    this.apiKey,

    this.baseURL,

    this.enterpriseUrl,

    this.setCacheKey,

    this.timeout,

    this.headerTimeout,

    this.chunkTimeout,
    Map<String, Object?> additionalProperties = const {},
  }) : _additionalProperties = Map.unmodifiable(additionalProperties);

  @JsonKey(name: r'apiKey', required: false, includeIfNull: false)
  final String? apiKey;

  @JsonKey(name: r'baseURL', required: false, includeIfNull: false)
  final String? baseURL;

  @JsonKey(name: r'enterpriseUrl', required: false, includeIfNull: false)
  final String? enterpriseUrl;

  @JsonKey(name: r'setCacheKey', required: false, includeIfNull: false)
  final bool? setCacheKey;

  @JsonKey(name: r'timeout', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion004? timeout;

  @JsonKey(name: r'headerTimeout', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion005? headerTimeout;

  @JsonKey(name: r'chunkTimeout', required: false, includeIfNull: false)
  final int? chunkTimeout;

  Map<String, Object?> _additionalProperties;

  @JsonKey(includeFromJson: false, includeToJson: false)
  Map<String, Object?> get additionalProperties => _additionalProperties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigOptions &&
            runtimeType == other.runtimeType &&
            equals(
              [
                apiKey,
                baseURL,
                enterpriseUrl,
                setCacheKey,
                timeout,
                headerTimeout,
                chunkTimeout,
              ],
              [
                other.apiKey,
                other.baseURL,
                other.enterpriseUrl,
                other.setCacheKey,
                other.timeout,
                other.headerTimeout,
                other.chunkTimeout,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        apiKey,
        baseURL,
        enterpriseUrl,
        setCacheKey,
        timeout,
        headerTimeout,
        chunkTimeout,
      ]);

  factory ProviderConfigOptions.fromJson(Map<String, dynamic> json) {
    final value = _$ProviderConfigOptionsFromJson(json);
    const knownKeys = <String>{
      r'apiKey',
      r'baseURL',
      r'enterpriseUrl',
      r'setCacheKey',
      r'timeout',
      r'headerTimeout',
      r'chunkTimeout',
    };
    value._additionalProperties = Map.unmodifiable({
      for (final entry in json.entries)
        if (!knownKeys.contains(entry.key)) entry.key: entry.value,
    });
    return value;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    for (final entry in additionalProperties.entries) entry.key: entry.value,
    ..._$ProviderConfigOptionsToJson(this),
  };

  String toString() {
    return toJson().toString();
  }
}
