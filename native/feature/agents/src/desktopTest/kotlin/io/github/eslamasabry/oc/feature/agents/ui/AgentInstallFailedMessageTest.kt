package io.github.eslamasabry.oc.feature.agents.ui

import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallFailure
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentVersions
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsInstallFailedNotStarted
import io.github.eslamasabry.oc.ui.words.res.agentsInstallFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.agentsInstallFailedVersion
import io.github.eslamasabry.oc.ui.words.res.agentsInstallFailedVersionOther
import io.github.eslamasabry.oc.ui.words.res.agentsInstallFailedWithdrawn
import io.github.eslamasabry.oc.ui.words.res.agentsInstallInterruptedNow
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString

/**
 * The words of a failed agent install's version check (2026-11-12: Gemini CLI said "the version that arrived is not
 * the one the app expects. Try again." for a download that had passed its checksum and only did not answer in time).
 */
class AgentInstallFailedMessageTest {
    private val saved = Locale.getDefault()

    @AfterTest fun restore() = Locale.setDefault(saved)

    private fun failed(failure: PhoneAgentInstallFailure, versions: PhoneAgentVersions? = null) =
        PhoneAgentInstallation("gemini", PhoneAgentInstallPhase.Failed, null, failure = failure, versions = versions)

    private suspend fun words(message: InstallFailedMessage): String =
        getString(message.words.resource(), *message.args.toTypedArray()).replace(Regex("[⁦⁧⁨⁩]"), "")

    @Test
    fun `another version names what arrived and what the app works with, and that only an update helps`() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        val versions = PhoneAgentVersions("0.65.0", "0.66.0")
        val message = installFailedMessage(failed(PhoneAgentInstallFailure.Version, versions), "Gemini CLI")
        assertEquals(Res.string.agentsInstallFailedVersionOther, message.words.resource())
        assertEquals(listOf("Gemini CLI", KitBidi.ltr("0.65.0"), KitBidi.ltr("0.66.0")), message.args)
        assertEquals(
            "Gemini CLI could not be installed: the download says it is version 0.65.0, and this app works with " +
                "0.66.0. Trying again will not change this; an update of this app will.",
            words(message),
        )
    }

    @Test
    fun `a program that did not answer says so and keeps Try again`() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        val message = installFailedMessage(failed(PhoneAgentInstallFailure.NotStarted), "Gemini CLI")
        assertEquals(Res.string.agentsInstallFailedNotStarted, message.words.resource())
        assertEquals(listOf("Gemini CLI"), message.args)
        assertEquals(
            "Gemini CLI was downloaded but did not start on this phone, so its version could not be checked. Try " +
                "again; if it happens again, Details has what the phone reported.",
            words(message),
        )
    }

    @Test
    fun `a withdrawn pinned version names it`() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        val versions = PhoneAgentVersions(null, "0.66.0")
        val message = installFailedMessage(failed(PhoneAgentInstallFailure.Withdrawn, versions), "Gemini CLI")
        assertEquals(Res.string.agentsInstallFailedWithdrawn, message.words.resource())
        assertEquals(listOf("Gemini CLI", KitBidi.ltr("0.66.0")), message.args)
        assertEquals(
            "Gemini CLI could not be installed: version 0.66.0, the one this app installs, is no longer offered for " +
                "download. Trying again will not change this; an update of this app will.",
            words(message),
        )
    }

    @Test
    fun `without the versions each kind keeps its own sentence`() {
        val version = installFailedMessage(failed(PhoneAgentInstallFailure.Version), "Gemini CLI")
        assertEquals(InstallFailedMessage(InstallFailedWords.Version, listOf("Gemini CLI")), version)
        assertEquals(Res.string.agentsInstallFailedVersion, version.words.resource())
        val withdrawn = installFailedMessage(failed(PhoneAgentInstallFailure.Withdrawn), "Gemini CLI")
        assertEquals(InstallFailedMessage(InstallFailedWords.Unknown, listOf("Gemini CLI")), withdrawn)
        assertEquals(Res.string.agentsInstallFailedUnknown, withdrawn.words.resource())
    }

    @Test
    fun `an install cut short keeps its own sentence whatever kind it recorded`() {
        val cut =
            PhoneAgentInstallation(
                "gemini",
                PhoneAgentInstallPhase.Interrupted,
                null,
                failure = PhoneAgentInstallFailure.Version,
                versions = PhoneAgentVersions("0.65.0", "0.66.0"),
            )
        val message = installFailedMessage(cut, "Gemini CLI")
        assertEquals(InstallFailedMessage(InstallFailedWords.Interrupted, listOf("Gemini CLI")), message)
        assertEquals(Res.string.agentsInstallInterruptedNow, message.words.resource())
    }

    @Test
    fun `the new sentences read in Arabic with the versions left to right`() = runTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val versions = PhoneAgentVersions("0.65.0", "0.66.0")
        val message = installFailedMessage(failed(PhoneAgentInstallFailure.Version, versions), "Gemini CLI")
        val text = getString(message.words.resource(), *message.args.toTypedArray())
        assertEquals(true, KitBidi.ltr("0.65.0") in text && KitBidi.ltr("0.66.0") in text, text)
        assertEquals(true, text.startsWith("تعذّر تثبيت Gemini CLI"), text)
    }
}
