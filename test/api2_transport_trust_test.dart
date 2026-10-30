import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/server_trust.dart';

class _NativeHttpOverrides extends HttpOverrides {}

Future<void> _withNativeHttp(Future<void> Function() body) =>
    HttpOverrides.runZoned(
      body,
      createHttpClient: (context) =>
          _NativeHttpOverrides().createHttpClient(context),
    );

SecurityContext _serverContext(String certificate, String key) =>
    SecurityContext()
      ..useCertificateChainBytes(utf8.encode(certificate))
      ..usePrivateKeyBytes(utf8.encode(key));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('HTTPS starts TLS and never writes a plaintext Basic request', () async {
    await _withNativeHttp(() async {
      final firstFrame = Completer<List<int>>();
      final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((socket) async {
        try {
          firstFrame.complete(await socket.first);
        } finally {
          socket.destroy();
        }
      });
      final transport = Api2Transport(
        baseUrl: 'https://127.0.0.1:${server.port}',
        password: 'private-test-password',
      );
      try {
        await expectLater(
          transport.getJson('/info'),
          throwsA(isA<Api2NetworkError>()),
        );
        final bytes = await firstFrame.future;
        expect(bytes.take(2), [
          0x16,
          0x03,
        ], reason: 'TLS handshake record precedes all HTTP');
        expect(latin1.decode(bytes), isNot(contains('Authorization:')));
        expect(latin1.decode(bytes), isNot(contains('GET /')));
      } finally {
        transport.close();
        await server.close();
      }
    });
  });

  test(
    'remote HTTP fails before a request without exact-origin consent',
    () async {
      final transport = Api2Transport(
        baseUrl: 'http://203.0.113.1:4098',
        password: 'private-test-password',
      );
      try {
        await expectLater(
          transport.health(),
          throwsA(
            isA<Api2NetworkError>()
                .having(
                  (e) => e.message,
                  'safe consent message',
                  contains('Confirm unencrypted HTTP'),
                )
                .having(
                  (e) => e.message,
                  'no password',
                  isNot(contains('private-test-password')),
                ),
          ),
        );
      } finally {
        transport.close();
      }
    },
  );

  test(
    'absolute URL cannot move the Basic credentials to another origin',
    () async {
      await _withNativeHttp(() async {
        var received = 0;
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          received++;
          await request.response.close();
        });
        final transport = Api2Transport(
          baseUrl: 'http://localhost:1',
          password: 'private-test-password',
        );
        try {
          await expectLater(
            transport.getJson('http://127.0.0.1:${server.port}/api/info'),
            throwsA(
              isA<Api2NetworkError>().having(
                (e) => e.message,
                'origin rejected',
                contains('trusted server address'),
              ),
            ),
          );
          expect(received, 0);
        } finally {
          transport.close();
          await server.close(force: true);
        }
      });
    },
  );

  test(
    'redirects stay blocked even if a caller enables followRedirects',
    () async {
      await _withNativeHttp(() async {
        var escaped = 0;
        final target = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        target.listen((request) async {
          escaped++;
          await request.response.close();
        });
        final source = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        source.listen((request) async {
          request.response.statusCode = HttpStatus.temporaryRedirect;
          request.response.headers.set(
            HttpHeaders.locationHeader,
            'http://127.0.0.1:${target.port}/capture',
          );
          await request.response.close();
        });
        final transport = Api2Transport(
          baseUrl: 'http://127.0.0.1:${source.port}',
          password: 'private-test-password',
        );
        try {
          await expectLater(
            transport.dio.get<dynamic>(
              '/info',
              options: Options(followRedirects: true),
            ),
            throwsA(
              isA<DioException>().having(
                (e) => e.response?.statusCode,
                'redirect returned',
                307,
              ),
            ),
          );
          expect(escaped, 0);
        } finally {
          transport.close();
          await source.close(force: true);
          await target.close(force: true);
        }
      });
    },
  );

  test(
    'inspect sends no HTTP; only a matching pin releases Basic auth',
    () async {
      await _withNativeHttp(() async {
        var requests = 0;
        final server = await HttpServer.bindSecure(
          InternetAddress.loopbackIPv4,
          0,
          _serverContext(_certificateA, _keyA),
        );
        server.listen((request) async {
          requests++;
          expect(
            request.headers.value(HttpHeaders.authorizationHeader),
            'Basic ${base64Encode(utf8.encode('opencode:private-test-password'))}',
          );
          request.response.headers.contentType = ContentType.json;
          request.response.write('{"version":"2.1.1"}');
          await request.response.close();
        });
        final base = 'https://127.0.0.1:${server.port}';
        try {
          final info = await inspectServerCertificate(base);
          expect(info.fingerprintSha256, hasLength(64));
          expect(requests, 0);
          final rejected = Api2Transport(
            baseUrl: base,
            password: 'private-test-password',
            certificatePinSha256: '0' * 64,
          );
          try {
            await expectLater(
              rejected.getJson('/info'),
              throwsA(
                isA<Api2NetworkError>()
                    .having(
                      (e) => e.message,
                      'changed identity',
                      contains('server certificate changed'),
                    )
                    .having(
                      (e) => e.message,
                      'no password',
                      isNot(contains('private-test-password')),
                    ),
              ),
            );
            expect(requests, 0);
          } finally {
            rejected.close();
          }
          final accepted = Api2Transport(
            baseUrl: base,
            password: 'private-test-password',
            certificatePinSha256: info.fingerprintSha256,
          );
          try {
            expect(await accepted.getJson('/info'), {'version': '2.1.1'});
            expect(requests, 1);
          } finally {
            accepted.close();
          }
        } finally {
          await server.close(force: true);
        }
      });
    },
  );

  test(
    'matching expired certificate blocks credentials with a distinct error',
    () async {
      await _withNativeHttp(() async {
        var requests = 0;
        final server = await HttpServer.bindSecure(
          InternetAddress.loopbackIPv4,
          0,
          _serverContext(_certificateExpired, _keyA),
        );
        server.listen((request) async {
          requests++;
          await request.response.close();
        });
        final base = 'https://127.0.0.1:${server.port}';
        try {
          final certificate = await inspectServerCertificate(base);
          final transport = Api2Transport(
            baseUrl: base,
            password: 'private-test-password',
            certificatePinSha256: certificate.fingerprintSha256,
          );
          try {
            await expectLater(
              transport.getJson('/info'),
              throwsA(
                isA<Api2NetworkError>().having(
                  (e) => e.message,
                  'validity failure',
                  contains('expired or not yet valid'),
                ),
              ),
            );
            expect(requests, 0);
          } finally {
            transport.close();
          }
        } finally {
          await server.close(force: true);
        }
      });
    },
  );

  test('untrusted TLS failures retain a safe certificate marker', () async {
    await _withNativeHttp(() async {
      var requests = 0;
      final server = await HttpServer.bindSecure(
        InternetAddress.loopbackIPv4,
        0,
        _serverContext(_certificateA, _keyA),
      );
      server.listen((request) async {
        requests++;
        await request.response.close();
      });
      final transport = Api2Transport(
        baseUrl: 'https://127.0.0.1:${server.port}',
        password: 'private-test-password',
      );
      try {
        await expectLater(
          transport.getJson('/info'),
          throwsA(
            isA<Api2NetworkError>().having(
              (e) => e.message,
              'safe TLS marker',
              'The server certificate could not be verified.',
            ),
          ),
        );
        expect(requests, 0);
      } finally {
        transport.close();
        await server.close(force: true);
      }
    });
  });

  test(
    'a replacement trusted by normal PKI still cannot bypass the existing pin',
    () async {
      await _withNativeHttp(() async {
        final original = await HttpServer.bindSecure(
          InternetAddress.loopbackIPv4,
          0,
          _serverContext(_certificateA, _keyA),
        );
        original.listen((request) async {
          await request.response.close();
        });
        final port = original.port;
        final base = 'https://127.0.0.1:$port';
        final pin = (await inspectServerCertificate(base)).fingerprintSha256;
        await original.close(force: true);
        // Trust B in this test process's normal root store. The pinned client's
        // deliberately empty root store must still reject this valid replacement.
        SecurityContext.defaultContext.setTrustedCertificatesBytes(
          utf8.encode(_certificateB),
        );
        var requests = 0;
        final replacement = await HttpServer.bindSecure(
          InternetAddress.loopbackIPv4,
          port,
          _serverContext(_certificateB, _keyB),
        );
        replacement.listen((request) async {
          requests++;
          request.response.headers.contentType = ContentType.json;
          request.response.write('{"version":"2.1.1"}');
          await request.response.close();
        });
        final normal = Api2Transport(baseUrl: base, password: '');
        final pinned = Api2Transport(
          baseUrl: base,
          password: 'private-test-password',
          certificatePinSha256: pin,
        );
        try {
          expect(await normal.getJson('/info'), {'version': '2.1.1'});
          expect(requests, 1);
          await expectLater(
            pinned.getJson('/info'),
            throwsA(
              isA<Api2NetworkError>().having(
                (e) => e.message,
                'changed certificate',
                contains('server certificate changed'),
              ),
            ),
          );
          expect(
            requests,
            1,
            reason: 'replacement received no pinned-client credentials',
          );
        } finally {
          normal.close();
          pinned.close();
          await replacement.close(force: true);
        }
      });
    },
  );
}

// Public, disposable test-only identities; never installed or used by the app.
const _certificateA = '''
-----BEGIN CERTIFICATE-----
MIIDJTCCAg2gAwIBAgIUTDJAVHohDolWnwYfruyMajrQmiwwDQYJKoZIhvcNAQEL
BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MTAxNTE5MDMyMVoXDTM2MTAx
MjE5MDMyMVowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
AAOCAQ8AMIIBCgKCAQEAqWjITdDg9ixbGDRFaS3PLblRZSZaQamf1I/IRWSjfnVJ
jKAto0hRFtUUEugRX3y3mzRUHZ34p5dbSruvytfIpJBj0+2B3trrOnGzC5xGsEYN
qTz943k9SQhzVgHjb8dyOr0vReHxQc8mIhFuEjS+tGrde0kkh8cZ5AT1iCzWAxLp
sQ9tGcxho6VMqGO2nbL9j8pKJ37nLsSG8t4V0byw/ljMdkCVyF1g4P25ZoFVX3mK
JWELe2TGqmd7uBEPj/ZcCsINpm4fvOaZZSEVLXTRtmRh9AKcJx7pFoZMsWDDdbHW
yd2RMEkF8dC3AFGktcUPx/9/KbQ8BmSfrVb4BIYXgQIDAQABo28wbTAdBgNVHQ4E
FgQUgXpDc6TtcURyOFSTWG89HtRnH68wHwYDVR0jBBgwFoAUgXpDc6TtcURyOFST
WG89HtRnH68wDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SH
BH8AAAEwDQYJKoZIhvcNAQELBQADggEBAAbJ38h0n8On2V+8kLWDIzyfMOELoaX5
u/TpwhH8JenZJqKH1SVG2osYGSU73eEGoBn7cq9Uth/sEWC7GZ0XElAt3Ym22Kof
ccYunXZxeD86sWf5NoeRjUvECOBABa534U49AR38s4L8X7RI0aZVwj+pLHku70/2
iMsVNGh3F00bYXDwdNm3HonnL+g56V/0Xc/NpxwcumIu/Qzv6WyLqm0ln57qb27/
0tRFZWD5BsgFcakokRzrbq9P4hqQb0GV56k8kNgLANxQWtWOn4cqDYiHrTkErKnu
hh/lDZ2GDlWXD+qErIV06arRr4K8dE1AT7D9RNWYqMBjb+9kmpoIT5c=
-----END CERTIFICATE-----
''';

const _keyA = '''
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCpaMhN0OD2LFsY
NEVpLc8tuVFlJlpBqZ/Uj8hFZKN+dUmMoC2jSFEW1RQS6BFffLebNFQdnfinl1tK
u6/K18ikkGPT7YHe2us6cbMLnEawRg2pPP3jeT1JCHNWAeNvx3I6vS9F4fFBzyYi
EW4SNL60at17SSSHxxnkBPWILNYDEumxD20ZzGGjpUyoY7adsv2PykonfucuxIby
3hXRvLD+WMx2QJXIXWDg/blmgVVfeYolYQt7ZMaqZ3u4EQ+P9lwKwg2mbh+85pll
IRUtdNG2ZGH0ApwnHukWhkyxYMN1sdbJ3ZEwSQXx0LcAUaS1xQ/H/38ptDwGZJ+t
VvgEhheBAgMBAAECggEAAPiCvVMayCIIoIUv2d2r8wSChr0V++5W5h+IfXwTOUzm
I3SdDlWzmKL5egLfppyT4WDfieeZrVEX8jYlHWzoCoDAFCdFBsgyqwX9TlpXW4JN
t5UpbW7hMBl8c2XfEIkQMgszya7hTQgBxLOXovP+a8KMsqjsylsA8jUVrso7mPE1
zIer0SffAyBpb7iTKIbNbmxhYSRn6ZgK5TLHJVrdwblHTK3dtzAT4bsZbHPl/Bi4
3jXg7PP+5uZAFu9DGv6MmQMLro4F7y38RPm4HzQUZiSaX5Hq8nyG7r5vxZzU4ft2
aq7zAi68wAujs5sx4y85QI/gIolCzJgwPEd24Nh+4QKBgQDWJuLD+WWudgXsOpwC
vU4szhGH0P/FxzjG4P3VABkrIOKwQa9aZ2AvRexYlj8dTVw8o042w39rxDaNHek4
3SCUNmpHYPbxTHSNn6OkQK6Hlo7scycPfWUKbsl75ZrmgauQ/BLXLByFP2oduxtS
b1OmsrHSD0H43PgeApuvNgEixQKBgQDKg50qMD15ci8XWGC+xFBkvSF0N3655JZt
vGp0HfrBWLfMbJ1xZ7yzXKCwgmERlcZwHEFa6nJn7Dj2ZObJ8FFJcIkDKVmxV6UT
gaTBXUXg/h14iBr1/yNZwEnwv9NDmHFkiaLE/aKV31YYlBisGq/T/fyNXDIB7Fez
KfCcY/k9jQKBgQCBJN4n1OTO+vnDZezjcgoM1xCXWSCFwwioVtJ8exyyd5h9Nn9r
A2Br4b0QBzt0jkyLfOgT+n2DHcbP/zPASQn2l4IsHXuONM8tARKMYT1fIxp52U+6
9aHTUtBK2UI6tAQk4P7zp/RjHsT9vPJTGjPWGXlG9lCQpaEzZ9s/JFA3CQKBgQCV
/TK7G218dFhyZu+Ieue8JOQRKkT+s1HCxsjlZYriBq5ihpN5ecal5VXSRU5VcuO9
BwBUsM25MJjjzMmuCQJ7bRDdOZQHqA1ITD3VRRrLUApNyiiFx8jL4YIiAEEdkFtz
hgj684umivo+S/mWBlHxpeyWWhFjoUM3HDnr6pOfxQKBgC4Xb6KmyRCPvGmTJ5f8
gB+oi5V9GmbphClrm3g51QNmV4lqNpyNoiX/RYO9gRLEfIj4tQv+oNJp2zNIIf8l
OpJDXTldXqNjSJZNIOgP5DgpFznpH27LCP/hdCOJVEgQ/zNzPUWdb2R3LqhddSCj
62FmoMsVhjd7fRPhrQ/Zf47t
-----END PRIVATE KEY-----
''';

const _certificateB = '''
-----BEGIN CERTIFICATE-----
MIIDJTCCAg2gAwIBAgIUUcXtnmtEBQzUKqKpP/QQiuGt+24wDQYJKoZIhvcNAQEL
BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MTAxNTE5MDMyMVoXDTM2MTAx
MjE5MDMyMVowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
AAOCAQ8AMIIBCgKCAQEAt0zdQr7/P8mVcwzcyo3FYlC64f1v8vQ+Xk7kS+VakILk
aPsV9DVWXxclvOPyOBKXsBlmbr9krCHGTeZFjqUEuPyVjGhJAxt5kYViJ/igXIEm
SFL97hnNscUdgyHOTzCyZ0sHX91xTZdX9i7t9y5C3KHIGBgxzpyPmBG32wepR7o6
2A4RVnQjfWwfKaOAAEc/nZhFapERn87CpA/JcnnQOIHnbIXBs0jEq/bWNFgSEGmz
gW9H/3/JCzdkTb0Fp1qwCmoEuMxQUZrAgO5nH0/DY+bUY9bWc6yDyJ5SIDxAadXX
K/19XXl4ZqjXyu2syIzv6vEUY2yPDWheWyTGL8/DAwIDAQABo28wbTAdBgNVHQ4E
FgQUJTMBjsCP7Dv9hL04I/SSnR/z6TQwHwYDVR0jBBgwFoAUJTMBjsCP7Dv9hL04
I/SSnR/z6TQwDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SH
BH8AAAEwDQYJKoZIhvcNAQELBQADggEBAJMQF0s6qhoLBRfwLv4vucE2kh/bep4X
lX3poFn9QXiSs9mNtZSg3NtkzJLntdG7L5XD2o5uuFHFeoxc5KnfCRP9UrzyJeds
tx7oKOMh6oXePeS81ZdWFopgUGF8M25zxGqhhnO7SHVmJgKmpDfy3ySsKWmgPxm3
nUx0KoXh43qVRw3IMpWdBAtTOgdvKAyqrhyvLQmppy9uzyt+Nvouzmtk02ceWC4d
+N2Ev3F2FDJMyR7jyCFGHCSH7kn1OVCN+ixcJCRGwYkKWlcc+VOYEvqFur776ROC
gWg8nEWcJXcRiBYOAzVyfuJWLAPEgTkkMQRTD0hNfLiHjNYawWNplaM=
-----END CERTIFICATE-----
''';

const _keyB = '''
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3TN1Cvv8/yZVz
DNzKjcViULrh/W/y9D5eTuRL5VqQguRo+xX0NVZfFyW84/I4EpewGWZuv2SsIcZN
5kWOpQS4/JWMaEkDG3mRhWIn+KBcgSZIUv3uGc2xxR2DIc5PMLJnSwdf3XFNl1f2
Lu33LkLcocgYGDHOnI+YEbfbB6lHujrYDhFWdCN9bB8po4AARz+dmEVqkRGfzsKk
D8lyedA4gedshcGzSMSr9tY0WBIQabOBb0f/f8kLN2RNvQWnWrAKagS4zFBRmsCA
7mcfT8Nj5tRj1tZzrIPInlIgPEBp1dcr/X1deXhmqNfK7azIjO/q8RRjbI8NaF5b
JMYvz8MDAgMBAAECggEANiGS7hUtmA7V/cX8piy7787bgcWwe9U5Xex6axduaWLe
RUpxL09ZFHfngyLl8z06Ypz9rRMEjfjjcTnhrMB65o5hM7fQaASDIEB9vkQkR200
ewNXTit8nVUS5Bjlrmk5Jlv3742b2kgph+BQEkEtKa71QnhSrSt7FHOXEi3vBeku
Yx0nK/85IsAR/h+B/4/I/+k8/Apf8Bj7guqbLtdh6CUGbHmw76ISPZ9YuOd99Gf7
lVVGHVZKhKPNLxGi+eo+2a6wzouiSh12j7suvDagI+NE/NXv83XbOyW5QCcBJmvW
5eVN1clklwG3WUa4yHpp17UVPSXqPknxzqDtakYAkQKBgQD110tHeTtSMkQRlAvQ
gwXldT+r+3Mnk37m0CR55AkisBD+fwf9w4hSUoNsbdXgnQk/K05bgB1bJoqFZD+1
i/I2GH/LtrELioxhwlESudW+cywUg6bhEtMGQxcmObZ+fJ2CuJxJxxVYgi5eYZxO
1HlGlL9sKArUjeemCx18gRWNbQKBgQC+3/aiskEwSr5iUMO7CSZBX6+LGvJ57YG7
Bo0vNbsVnHRruapZVXt7RMTV5D2V2McSTiDFu9PeNOhQ+ObQEsSN5IIye+xu5Xto
ETV2lWTnaQ1huQyGisGKNNsNqiZl+nyyf/gngoHvDV98agE1iqPOxKfX35iM3IOn
t9N9R4J8LwKBgQDYTtatzL2lVRUPYnQCw0Ys3z1m1Zg8+kpuTDXMChj/rGtHgf5G
CgWoaXG5teq593JrYm9PkypBSWgYdlyYIMq9TlEjrVuaDA349kDfeSPYrv3aDvyG
jUmf+OgleiJYqMzueAEL6V+ewOyLntcphUowEDFduN3OqA1qy9zl765HAQKBgQC+
UwvWhu/Be4YC+o1wvlrK7OJZTnFhapXlK080N9toXS0pDWTy1u3dqGCs9n0CIbm+
YvECuZs1AoRz2Pwtlckvwx6fwDmtCl3XEvHAYI7QwEt7mc0HiuwZJ0MViQfov6//
POLwYo21FByWLg0umiNkVWsXMfkm2+rbHc1gKiHOOQKBgG5ICE8ttdeggjB7+vlp
MGmuprxOFR75eF7AF9ffr4TKBhne1BHbSkEYfsRcftk0TQly+23v47pxU5DuZM9I
t1PAKHpyhgTIWihv3j6q3p9HVVzFvXyDnwetlV7ccVhJD+m83LJ0fII5E3oUM0Nz
NJtv5wevajKBASi9QHCU+rtz
-----END PRIVATE KEY-----
''';

const _certificateExpired = '''
-----BEGIN CERTIFICATE-----
MIICrzCCAZcCFAFa8tiXD+lrsRPdv2hgBT14hdH8MA0GCSqGSIb3DQEBCwUAMBQx
EjAQBgNVBAMMCWxvY2FsaG9zdDAeFw0yNjEwMTUxOTA2NDBaFw0yNjEwMTQxOTA2
NDBaMBQxEjAQBgNVBAMMCWxvY2FsaG9zdDCCASIwDQYJKoZIhvcNAQEBBQADggEP
ADCCAQoCggEBAKloyE3Q4PYsWxg0RWktzy25UWUmWkGpn9SPyEVko351SYygLaNI
URbVFBLoEV98t5s0VB2d+KeXW0q7r8rXyKSQY9Ptgd7a6zpxswucRrBGDak8/eN5
PUkIc1YB42/Hcjq9L0Xh8UHPJiIRbhI0vrRq3XtJJIfHGeQE9Ygs1gMS6bEPbRnM
YaOlTKhjtp2y/Y/KSid+5y7EhvLeFdG8sP5YzHZAlchdYOD9uWaBVV95iiVhC3tk
xqpne7gRD4/2XArCDaZuH7zmmWUhFS100bZkYfQCnCce6RaGTLFgw3Wx1sndkTBJ
BfHQtwBRpLXFD8f/fym0PAZkn61W+ASGF4ECAwEAATANBgkqhkiG9w0BAQsFAAOC
AQEAIZ7Mkb5u5WA21tiWitpIutREfN//4lRkSK4x5pZaK/8w8C1XG5sXeqJbCqqs
IGG1mUzsiOYqUZK0Pc15oLucNvE6chBGoTigh1xHp5++z2qKel3xrvIXDue6+pmG
eXWaahaqi3sGiJNfdH0MQqJNoTfaLnV5UNQRLxdgNt3rVI4540CzOKG4FPgG/7PA
0LTs1j/lkZnXhIsIcIeWX9nml7iTWzyavDlFO29UDnXW2I77U4mZyFgI7/jDTbzj
RyKkK3ne4l3TJvyJNWYa+70o9Wu09n/RQtOQ1CXgZ4H6DGOfjnC6Rpls8voQexi0
jnxJIrWPt5D5PXCNOdeggmROsw==
-----END CERTIFICATE-----
''';
