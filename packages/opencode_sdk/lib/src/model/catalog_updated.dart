//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'catalog_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class CatalogUpdated {
  /// Returns a new [CatalogUpdated] instance.
  CatalogUpdated({
    required this.id,

    this.metadata,

    required this.type,

    this.durable,

    this.location,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: CatalogUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final CatalogUpdatedTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final Object data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is CatalogUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, type, durable, location, data],
              [
                other.id,
                other.metadata,
                other.type,
                other.durable,
                other.location,
                other.data,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, type, durable, location, data]);

  factory CatalogUpdated.fromJson(Map<String, dynamic> json) =>
      _$CatalogUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$CatalogUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum CatalogUpdatedTypeEnum {
  @JsonValue(r'catalog.updated')
  catalogPeriodUpdated(r'catalog.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const CatalogUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
