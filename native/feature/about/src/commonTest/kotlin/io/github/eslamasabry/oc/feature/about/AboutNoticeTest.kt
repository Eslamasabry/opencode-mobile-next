package io.github.eslamasabry.oc.feature.about

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.about.presentation.AboutNotice
import io.github.eslamasabry.oc.feature.about.presentation.NoticesPhase
import io.github.eslamasabry.oc.feature.about.presentation.NoticesSource
import io.github.eslamasabry.oc.feature.about.presentation.NoticesViewModel
import io.github.eslamasabry.oc.feature.about.presentation.ProvenanceLine
import io.github.eslamasabry.oc.feature.about.ui.AboutPendingWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The build notice and the open source notices (test/about_alpha_notice_test.dart:207,218). */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutNoticeTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private val file =
        """
        # Third-Party Notices

        OpenCode Mobile is an independent community project.

        **How this file is verified.** Every version below is read from pubspec.lock.

        ## Bundled components

        ### Phosphor icon artwork

        MIT.

        ## Package inventory

        | Package | License |

        ## Regenerating this file

        Run the tool.
        """.trimIndent()

    @Test
    fun `B-NAV-001 the notice keeps provenance and desktop limits and makes no release-channel claim`() {
        assertEquals(ProvenanceLine.entries.toList(), AboutNotice.provenance.toList())
        val english = AboutNotice.provenance.joinToString(" ") { AboutPendingWords.provenance(it).en }
        assertTrue("AI assistance" in english)
        assertTrue("Android is the primary supported" in english)
        assertTrue("not been hardware-tested" in english)
        for (claim in listOf("public alpha", "alpha", "beta", "stable", "preview")) assertFalse(claim in english.lowercase(), claim)
        // Arabic reads every line too, and never names a channel.
        val arabic = AboutNotice.provenance.map { AboutPendingWords.provenance(it).ar }
        assertTrue(arabic.all { it.isNotBlank() && it != english })
        assertFalse(arabic.any { "ألفا" in it || "بيتا" in it })
    }

    @Test
    fun `B-NAV-001 the open source notices leave out the maintainer intro and the regeneration steps`() {
        val shown = AboutNotice.noticesForReaders(file)
        assertTrue(shown.startsWith("## Bundled components"))
        assertTrue("## Package inventory" in shown)
        assertFalse("independent community project" in shown)
        assertFalse("How this file is verified" in shown)
        assertFalse("Regenerating" in shown)
        assertEquals("no sections", AboutNotice.noticesForReaders("no sections"))
    }

    @Test
    fun `B-NAV-001 the notices page loads the file for readers, says when it cannot, and tries again`() = runTest {
        var answer: Outcome<String> = Outcome.Failed(Failure.Unexpected())
        val nav = FakeNavigator()
        val vm = NoticesViewModel(NoticesSource { answer }, nav)
        assertEquals(NoticesPhase.Failed, vm.state.value.phase)
        assertEquals("", vm.state.value.text)
        answer = file.ok()
        vm.onRetry()
        assertEquals(NoticesPhase.Ready, vm.state.value.phase)
        assertTrue(vm.state.value.text.startsWith("## Bundled components"))
        assertEquals(AboutNotice.provenance, vm.state.value.provenance)
        vm.onBack()
        assertEquals(1, nav.backs)
    }
}
