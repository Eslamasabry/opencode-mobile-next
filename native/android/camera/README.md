# :android:camera

Unit QR-CAMERA (report: `docs/native/units/QR-CAMERA.md`). The camera seams for the pairing scanner and in-app photo
capture. Google-free: CameraX + zxing-cpp, no ML Kit, no Google Play services (plan 02 G8, plan 10 §2.7).

| Folder | What | Builds today |
|---|---|---|
| `src/main/.../scan` | `ScanWindow` crop, `QrFrameDecoder`/`QrScanOptions`, `ScanSession` (latch, once-per-sighting rejects, stale starts, digests only) | yes |
| `src/main/.../access` | `ScannerStage`/`ScannerAction`, `CameraAccessFlow` (permission in three states), `CameraFailures`, `StartingNotice`, `AndroidCameraHardware` | yes |
| `src/main/.../capture` | `CaptureFiles` (`files/camera-capture-v1/<id>.jpg`), `PhotoCapturer` | yes |
| `src/camerax/.../camerax` | `ZxingCppQrDecoder`, `CameraXScanner`, `CameraXPhotoCapturer`, `CameraXBinding` | only when the catalog has `camerax-core`, `camerax-camera2`, `camerax-lifecycle`, `zxing-cpp` **and** build-logic `ExternalRules` lets `:android:camera` declare `androidx.camera` and `io.github.zxing-cpp` |

Tests: `./gradlew :android:camera:testDebugUnitTest` (host JVM; QR fixtures in `src/test/resources/qr`, decoded with
ZXing core as the stand-in for zxing-cpp, which has no host build). Regenerate fixtures with `tools/GenQrFixtures.java`.
