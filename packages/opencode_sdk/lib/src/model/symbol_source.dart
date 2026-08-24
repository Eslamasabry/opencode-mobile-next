//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/range.dart';
import 'package:opencode_sdk/src/model/file_part_source_text.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'symbol_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SymbolSource {
  /// Returns a new [SymbolSource] instance.
  SymbolSource({
    required this.text,

    required this.type,

    required this.path,

    required this.range,

    required this.name,

    required this.kind,
  });

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final FilePartSourceText text;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SymbolSourceTypeEnum.unknownDefaultOpenApi,
  )
  final SymbolSourceTypeEnum type;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'range', required: true, includeIfNull: false)
  final Range range;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  // minimum: 0
  @JsonKey(name: r'kind', required: true, includeIfNull: false)
  final int kind;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SymbolSource &&
            runtimeType == other.runtimeType &&
            equals(
              [text, type, path, range, name, kind],
              [
                other.text,
                other.type,
                other.path,
                other.range,
                other.name,
                other.kind,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([text, type, path, range, name, kind]);

  factory SymbolSource.fromJson(Map<String, dynamic> json) =>
      _$SymbolSourceFromJson(json);

  Map<String, dynamic> toJson() => _$SymbolSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SymbolSourceTypeEnum {
  @JsonValue(r'symbol')
  symbol(r'symbol'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SymbolSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
