# `:ui:kit-content` — part index

Content parts (plan 06 §3.1, §7.7; plan 10 §9, §11, §14). Package `io.github.eslamasabry.oc.ui.kit.content.*`,
namespace `io.github.eslamasabry.oc.ui.kitcontent`. Words come from `:ui:words` (`strings_kit.xml`); host labels are
parameters.

| Part | File | Replaces (Dart) | States | Tests | Golden |
|---|---|---|---|---|---|
| `KitQr` (+ `KitQrs.fits`) | `qr/KitQr.kt` | `KitQr` | code; too long | `QrPayloadTest`, `ContentPartsDesktopTest`, `KitQrReadabilityTest` | `KitQr/code-and-too-long-*` |
| `QrEncoder`, `QrRules`, `QrMatrix`, `QrPayload` (pure) | `qr/` | `qr` 3.0.2 package | — | `QrPayloadTest` | — |
| `KitZoom`, `KitZoomState`, `ZoomMath` | `image/` | `KitZoom`, `KitZoomController` | fitted; zoomed | `ZoomMathTest` | via `KitImageView` |
| `KitZoomControls` | `image/KitZoomControls.kt` | gallery/viewer zoom buttons | fitted; zoomed; at max | `ContentPartsDesktopTest` | via `KitImageView` |
| `KitImageView` | `image/KitImageView.kt` | viewer Image body | fitted; zoomed | — | `KitImageView/fitted-*` |
| `KitViewerFrame`, `KitViewerAction` | `viewer/KitViewerFrame.kt` | `KitViewer` frame | content | `ViewerRulesTest`, `ContentPartsDesktopTest` | `KitViewerFrame/text-*` |
| `KitViewerTextBody` | `viewer/KitViewerTextBody.kt` | viewer Text body | content; empty; truncated | `ContentPartsDesktopTest` | `KitViewerFrame/text-*` |
| `KitViewerCodeBody` | `viewer/KitViewerCodeBody.kt` | viewer Code body | content; empty; truncated; at a line | `ContentPartsDesktopTest` | `KitViewerCodeBody/at-line-5-*` |
| `KitViewerBinaryBody` | `viewer/KitViewerBinaryBody.kt` | viewer Binary body | one | `ContentPartsDesktopTest` | `KitViewerBinaryBody/zip-*` |
| `KitPdfPage`, `KitPdfPageState` | `viewer/KitPdfPage.kt` | `KitPdfPage` | loading; ready; failed | `ContentPartsDesktopTest` | `KitPdfPage/ready-loading-failed-*` |
| `ViewerPaths`, `ViewerText` (pure) | `viewer/` | `KitViewer.folderOf`, `_sizeInWords` | — | `ViewerRulesTest` | — |
| `PdfLimits`, `PdfCheck` (pure) | `pdf/PdfLimits.kt` | `LocalPdf` budgets | — | `PdfLimitsTest` | — |
| `PdfPageRasterizer` (androidMain) | `androidMain/…/pdf/PdfPageRasterizer.kt` | `LocalPdfService` render step | — | `KitQrReadabilityTest` (output cap) | — |

Goldens: `src/screenshots/<Part>/<scene>-<light|dark>-<en|ar>.png`, 412 × 915 dp at 420 dpi, motion Off.
Record `./gradlew :ui:kit-content:recordRoborazziAndroidHostTest`; verify `…:verifyRoborazziAndroidHostTest`.
