//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union007.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_cost.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_variants_value.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_limit.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_provider.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_modalities.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config_models_value.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfigModelsValue {
  /// Returns a new [ProviderConfigModelsValue] instance.
  ProviderConfigModelsValue({
    this.id,

    this.name,

    this.family,

    this.releaseDate,

    this.attachment,

    this.reasoning,

    this.temperature,

    this.toolCall,

    this.interleaved,

    this.cost,

    this.limit,

    this.modalities,

    this.experimental,

    this.status,

    this.provider,

    this.options,

    this.headers,

    this.variants,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  @JsonKey(name: r'family', required: false, includeIfNull: false)
  final String? family;

  @JsonKey(name: r'release_date', required: false, includeIfNull: false)
  final String? releaseDate;

  @JsonKey(name: r'attachment', required: false, includeIfNull: false)
  final bool? attachment;

  @JsonKey(name: r'reasoning', required: false, includeIfNull: false)
  final bool? reasoning;

  @JsonKey(name: r'temperature', required: false, includeIfNull: false)
  final bool? temperature;

  @JsonKey(name: r'tool_call', required: false, includeIfNull: false)
  final bool? toolCall;

  @JsonKey(name: r'interleaved', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion007? interleaved;

  @JsonKey(name: r'cost', required: false, includeIfNull: false)
  final ProviderConfigModelsValueCost? cost;

  @JsonKey(name: r'limit', required: false, includeIfNull: false)
  final ProviderConfigModelsValueLimit? limit;

  @JsonKey(name: r'modalities', required: false, includeIfNull: false)
  final ProviderConfigModelsValueModalities? modalities;

  @JsonKey(name: r'experimental', required: false, includeIfNull: false)
  final bool? experimental;

  @JsonKey(
    name: r'status',
    required: false,
    includeIfNull: false,
    unknownEnumValue: ProviderConfigModelsValueStatusEnum.unknownDefaultOpenApi,
  )
  final ProviderConfigModelsValueStatusEnum? status;

  @JsonKey(name: r'provider', required: false, includeIfNull: false)
  final ProviderConfigModelsValueProvider? provider;

  @JsonKey(name: r'options', required: false, includeIfNull: false)
  final Object? options;

  @JsonKey(name: r'headers', required: false, includeIfNull: false)
  final Map<String, String>? headers;

  /// Variant-specific configuration
  @JsonKey(name: r'variants', required: false, includeIfNull: false)
  final Map<String, ProviderConfigModelsValueVariantsValue>? variants;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfigModelsValue &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                name,
                family,
                releaseDate,
                attachment,
                reasoning,
                temperature,
                toolCall,
                interleaved,
                cost,
                limit,
                modalities,
                experimental,
                status,
                provider,
                options,
                headers,
                variants,
              ],
              [
                other.id,
                other.name,
                other.family,
                other.releaseDate,
                other.attachment,
                other.reasoning,
                other.temperature,
                other.toolCall,
                other.interleaved,
                other.cost,
                other.limit,
                other.modalities,
                other.experimental,
                other.status,
                other.provider,
                other.options,
                other.headers,
                other.variants,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        name,
        family,
        releaseDate,
        attachment,
        reasoning,
        temperature,
        toolCall,
        interleaved,
        cost,
        limit,
        modalities,
        experimental,
        status,
        provider,
        options,
        headers,
        variants,
      ]);

  factory ProviderConfigModelsValue.fromJson(Map<String, dynamic> json) =>
      _$ProviderConfigModelsValueFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderConfigModelsValueToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderConfigModelsValueStatusEnum {
  @JsonValue(r'alpha')
  alpha(r'alpha'),
  @JsonValue(r'beta')
  beta(r'beta'),
  @JsonValue(r'deprecated')
  deprecated(r'deprecated'),
  @JsonValue(r'active')
  active(r'active'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderConfigModelsValueStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
