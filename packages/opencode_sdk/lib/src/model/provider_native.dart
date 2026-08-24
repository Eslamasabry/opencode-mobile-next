//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_native.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderNative {
  /// Returns a new [ProviderNative] instance.
  ProviderNative({required this.type, this.url, required this.settings});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderNativeTypeEnum.unknownDefaultOpenApi,
  )
  final ProviderNativeTypeEnum type;

  @JsonKey(name: r'url', required: false, includeIfNull: false)
  final String? url;

  @JsonKey(name: r'settings', required: true, includeIfNull: false)
  final Object settings;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderNative &&
            runtimeType == other.runtimeType &&
            equals(
              [type, url, settings],
              [other.type, other.url, other.settings],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, url, settings]);

  factory ProviderNative.fromJson(Map<String, dynamic> json) =>
      _$ProviderNativeFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderNativeToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderNativeTypeEnum {
  @JsonValue(r'native')
  native_(r'native'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderNativeTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
