package io.github.eslamasabry.oc.ui.kit.rb.o45

import io.github.eslamasabry.oc.ui.kit.scanner.scannerSide
import kotlin.test.Test
import kotlin.test.assertEquals

class ScannerWindowTest {
    @Test fun `the window stays square within gutters and margins and caps at Flutter's side`() {
        assertEquals(240f, scannerSide(412f, 700f, 16f, 24f))
        assertEquals(152f, scannerSide(412f, 200f, 16f, 24f))
        assertEquals(167f, scannerSide(199.9f, 700f, 16f, 24f))
        assertEquals(0f, scannerSide(20f, 20f, 16f, 24f))
    }
}
