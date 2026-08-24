//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/symbol_location.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'symbol.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Symbol {
  /// Returns a new [Symbol] instance.
  Symbol({required this.name, required this.kind, required this.location});

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  // minimum: 0
  @JsonKey(name: r'kind', required: true, includeIfNull: false)
  final int kind;

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final SymbolLocation location;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Symbol &&
            runtimeType == other.runtimeType &&
            equals(
              [name, kind, location],
              [other.name, other.kind, other.location],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([name, kind, location]);

  factory Symbol.fromJson(Map<String, dynamic> json) => _$SymbolFromJson(json);

  Map<String, dynamic> toJson() => _$SymbolToJson(this);

  String toString() {
    return toJson().toString();
  }
}
