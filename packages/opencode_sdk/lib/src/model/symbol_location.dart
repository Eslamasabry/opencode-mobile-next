//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/range.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'symbol_location.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SymbolLocation {
  /// Returns a new [SymbolLocation] instance.
  SymbolLocation({required this.uri, required this.range});

  @JsonKey(name: r'uri', required: true, includeIfNull: false)
  final String uri;

  @JsonKey(name: r'range', required: true, includeIfNull: false)
  final Range range;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SymbolLocation &&
            runtimeType == other.runtimeType &&
            equals([uri, range], [other.uri, other.range]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([uri, range]);

  factory SymbolLocation.fromJson(Map<String, dynamic> json) =>
      _$SymbolLocationFromJson(json);

  Map<String, dynamic> toJson() => _$SymbolLocationToJson(this);

  String toString() {
    return toJson().toString();
  }
}
