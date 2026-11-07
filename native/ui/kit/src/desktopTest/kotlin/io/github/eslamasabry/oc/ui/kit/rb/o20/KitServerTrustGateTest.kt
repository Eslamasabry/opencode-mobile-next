package io.github.eslamasabry.oc.ui.kit.rb.o20

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.modal.KitServerTrustBody
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import kotlin.test.Test

/**
 * test/goldens/kit/kit_server_trust_golden_test.dart `_warning`: a changed certificate (with its validity, written as
 * Flutter's en `formatFullDate` writes 2026-10-01 and 2027-10-01) and a public HTTP origin, at every gallery size.
 */
class KitServerTrustGateTest {
    @Composable
    private fun Warning(certificate: Boolean = true) {
        KitServerTrustBody(
            origin = if (certificate) "https://private.example:4099" else "http://public.example:4098",
            fingerprintSha256 = if (certificate) "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" else null,
            certificateChanged = certificate,
            validFrom = if (certificate) "Thursday, October 1, 2026" else null,
            validTo = if (certificate) "Friday, October 1, 2027" else null,
        )
    }

    private fun cert(name: String) = FlutterGoldenGate.check(name, wrap = { FlutterCapture(it) }) { Warning() }
    private fun http(name: String) = FlutterGoldenGate.check(name, wrap = { FlutterCapture(it) }) { Warning(certificate = false) }

    @Test fun kit_server_trust_certificate() { cert("kit_server_trust_certificate") }
    @Test fun kit_server_trust_certificate_360x800() { cert("kit_server_trust_certificate_360x800") }
    @Test fun kit_server_trust_certificate_800x1280() { cert("kit_server_trust_certificate_800x1280") }
    @Test fun kit_server_trust_certificate_1280x800() { cert("kit_server_trust_certificate_1280x800") }
    @Test fun kit_server_trust_certificate_1600x1000() { cert("kit_server_trust_certificate_1600x1000") }
    @Test fun kit_server_trust_certificate_text2() { cert("kit_server_trust_certificate_text2") }
    @Test fun kit_server_trust_certificate_text2_1280x800() { cert("kit_server_trust_certificate_text2_1280x800") }
    @Test fun kit_server_trust_http() { http("kit_server_trust_http") }
    @Test fun kit_server_trust_http_360x800() { http("kit_server_trust_http_360x800") }
    @Test fun kit_server_trust_http_800x1280() { http("kit_server_trust_http_800x1280") }
    @Test fun kit_server_trust_http_1280x800() { http("kit_server_trust_http_1280x800") }
    @Test fun kit_server_trust_http_1600x1000() { http("kit_server_trust_http_1600x1000") }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_server_trust", caps = mapOf("KitServerTrustBody" to "sheet", "showKitServerTrust" to "sheet")) { Warning() }
    }
}
