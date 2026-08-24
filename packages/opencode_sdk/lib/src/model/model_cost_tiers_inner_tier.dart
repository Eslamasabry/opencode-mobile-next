//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_cost_tiers_inner_tier.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCostTiersInnerTier {
  /// Returns a new [ModelCostTiersInnerTier] instance.
  ModelCostTiersInnerTier({required this.type, required this.size});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ModelCostTiersInnerTierTypeEnum.unknownDefaultOpenApi,
  )
  final ModelCostTiersInnerTierTypeEnum type;

  @JsonKey(name: r'size', required: true, includeIfNull: false)
  final num size;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCostTiersInnerTier &&
            runtimeType == other.runtimeType &&
            equals([type, size], [other.type, other.size]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, size]);

  factory ModelCostTiersInnerTier.fromJson(Map<String, dynamic> json) =>
      _$ModelCostTiersInnerTierFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCostTiersInnerTierToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ModelCostTiersInnerTierTypeEnum {
  @JsonValue(r'context')
  context(r'context'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ModelCostTiersInnerTierTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
