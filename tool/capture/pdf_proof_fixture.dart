import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

/// Synthetic two-page PDF, generated locally. Not an app asset or user file.
Uint8List makePdfProofFixture(bool highEntropy) {
  final objects = <Uint8List>[];
  Uint8List ascii(String value) => Uint8List.fromList(latin1.encode(value));
  Uint8List stream(String dictionary, Uint8List bytes) =>
      (BytesBuilder()
            ..add(ascii('<< $dictionary /Length ${bytes.length} >>\nstream\n'))
            ..add(bytes)
            ..add(ascii('\nendstream')))
          .takeBytes();
  const edge = 1280;
  final random = Random(81423);
  final rgb = Uint8List(edge * edge * 3);
  for (var i = 0; i < rgb.length; i++) {
    rgb[i] = highEntropy ? random.nextInt(256) : (i % 3 == 1 ? 170 : 40);
  }
  objects.add(ascii('<< /Type /Catalog /Pages 2 0 R >>'));
  objects.add(ascii('<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>'));
  objects.add(
    ascii(
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << /Font << /F1 8 0 R >> >> /Contents 4 0 R >>',
    ),
  );
  objects.add(
    stream(
      '',
      ascii(
        '0.1 0.5 0.4 rg 30 400 340 140 re f BT /F1 24 Tf 30 350 Td (Local PDF proof) Tj ET',
      ),
    ),
  );
  objects.add(
    ascii(
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $edge $edge] /Resources << /XObject << /Image 7 0 R >> >> /Contents 6 0 R >>',
    ),
  );
  objects.add(stream('', ascii('q $edge 0 0 $edge 0 0 cm /Image Do Q')));
  objects.add(
    stream(
      '/Type /XObject /Subtype /Image /Width $edge /Height $edge /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode',
      Uint8List.fromList(zlib.encode(rgb)),
    ),
  );
  objects.add(ascii('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'));
  final output = BytesBuilder()..add(ascii('%PDF-1.4\n'));
  final offsets = <int>[];
  for (var i = 0; i < objects.length; i++) {
    offsets.add(output.length);
    output
      ..add(ascii('${i + 1} 0 obj\n'))
      ..add(objects[i])
      ..add(ascii('\nendobj\n'));
  }
  final xref = output.length;
  output.add(ascii('xref\n0 ${objects.length + 1}\n0000000000 65535 f \n'));
  for (final offset in offsets) {
    output.add(ascii('${offset.toString().padLeft(10, '0')} 00000 n \n'));
  }
  output.add(
    ascii(
      'trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n',
    ),
  );
  return output.takeBytes();
}
